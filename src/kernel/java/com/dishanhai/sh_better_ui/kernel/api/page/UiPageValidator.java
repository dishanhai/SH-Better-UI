// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.dishanhai.sh_better_ui.kernel.api.page.UiPageProblem.Code.*;

/** Validates page structure and resolves render capability before the fixed tree is mounted. */
public final class UiPageValidator {
    private final Map<UiId, UiComponentDescriptor> descriptors;
    private final UiPageLimits limits;

    public UiPageValidator() {
        this(UiBuiltinComponents.descriptors());
    }

    public UiPageValidator(Map<UiId, UiComponentDescriptor> descriptors) {
        this(descriptors, UiPageLimits.DEFAULT);
    }

    public UiPageValidator(Map<UiId, UiComponentDescriptor> descriptors, UiPageLimits limits) {
        this.descriptors = Map.copyOf(descriptors);
        this.descriptors.forEach((id, descriptor) -> {
            if (!id.equals(descriptor.typeId())) {
                throw new IllegalArgumentException("Component registry key and descriptor do not match");
            }
        });
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Static validation against the supplied descriptor catalog. */
    public List<UiPageProblem> validate(UiPageDefinition page, boolean remote) {
        return inspect(page, remote, Optional.empty()).problems();
    }

    public void requireValid(UiPageDefinition page, boolean remote) {
        List<UiPageProblem> problems = validate(page, remote);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(problems.get(0).toString());
        }
    }

    /** Actual renderer versions and feature capabilities supplied by the host. */
    public UiResolvedPage resolve(UiPageDefinition page, boolean remote, Map<UiId, UiVersion> availableCapabilities) {
        return inspect(page, remote, Optional.of(Map.copyOf(availableCapabilities)));
    }

    private UiResolvedPage inspect(UiPageDefinition page, boolean remote, Optional<Map<UiId, UiVersion>> capabilities) {
        Objects.requireNonNull(page, "page");
        List<UiPageProblem> problems = new ArrayList<>();
        if (!compatible(UiPageDefinition.SCHEMA_VERSION, page.schemaVersion())) {
            problems.add(problem(SCHEMA_VERSION, "$", "schemaVersion",
                    UiPageDefinition.SCHEMA_VERSION.toString(), page.schemaVersion().toString(),
                    "Use a schema version supported by this client."));
        }
        if (page.dataSchema().fields().size() > limits.maxDataFields() || page.actions().size() > limits.maxActions()) {
            problems.add(problem(RESOURCE_LIMIT, "$", "", "bounded fields and actions", "entry limit exceeded",
                    "Reduce the declared data fields or actions."));
        }
        page.actions().forEach((key, action) -> {
            if (!key.equals(action.id()) || !key.namespace().equals(page.id().namespace())) {
                problems.add(problem(INVALID_ACTION, "$.actions", key.toString(),
                        "matching action ID in " + page.id().namespace(), action.id().toString(),
                        "Use action IDs owned by the page namespace."));
            }
            if (action.payloadSchema().fields().size() > limits.maxDataFields()) {
                problems.add(problem(RESOURCE_LIMIT, "$.actions", key.toString(), "bounded action fields",
                        "entry limit exceeded", "Reduce the declared action payload fields."));
            }
        });
        capabilities.ifPresent(available -> page.options().requiredCapabilities().forEach((id, minimum) -> {
            if (!compatible(available.get(id), minimum)) {
                problems.add(problem(CAPABILITY_MISSING, "$.options", id.toString(), minimum.toString(),
                        String.valueOf(available.get(id)), "Install a renderer with the required capability."));
            }
        }));
        if (!problems.isEmpty()) {
            return new UiResolvedPage(Optional.empty(), problems, List.of());
        }

        Inspection inspection = new Inspection(page, remote, capabilities, problems);
        inspection.checkStructure(page.root(), "root", 1);
        if (!problems.isEmpty()) {
            return new UiResolvedPage(Optional.empty(), problems, List.of());
        }
        UiNode root = inspection.resolve(page.root(), "root", true);
        if (root == null || !problems.isEmpty()) {
            return new UiResolvedPage(Optional.empty(), problems, inspection.degradations);
        }
        UiPageDefinition effective = root == page.root() ? page : new UiPageDefinition(page.id(), page.schemaVersion(),
                page.type(), root, page.dataSchema(), page.actions(), page.options());
        return new UiResolvedPage(Optional.of(effective), problems, inspection.degradations);
    }

    private final class Inspection {
        private final UiPageDefinition page;
        private final boolean remote;
        private final Optional<Map<UiId, UiVersion>> capabilities;
        private final List<UiPageProblem> problems;
        private final List<String> degradations = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();
        private final Map<String, UiId> nodeTypes = new java.util.HashMap<>();
        private final UiValueSchema.Budget values = new UiValueSchema.Budget();
        private int nodeCount;

        private Inspection(UiPageDefinition page, boolean remote, Optional<Map<UiId, UiVersion>> capabilities,
                           List<UiPageProblem> problems) {
            this.page = page;
            this.remote = remote;
            this.capabilities = capabilities;
            this.problems = problems;
        }

        private void checkStructure(UiNode node, String path, int depth) {
            if (++nodeCount > limits.maxNodes() || depth > limits.maxDepth()
                    || node.children().size() > limits.maxChildren()
                    || node.properties().size() + node.bindings().size() > limits.maxProperties()) {
                problems.add(problem(RESOURCE_LIMIT, path, "", "bounded node, depth and property counts",
                        "tree limit exceeded", "Reduce the tree depth, node count or per-node entries."));
                return;
            }
            if (!ids.add(node.id())) {
                problems.add(problem(DUPLICATE_ID, path, "id", "unique stable node ID", node.id(),
                        "Assign a stable ID that is unique within the page."));
            }
            nodeTypes.put(node.id(), node.typeId());
            checkProperties(node.properties(), node.bindings(), path);
            node.alternative().ifPresent(alternative ->
                    checkProperties(alternative.properties(), alternative.bindings(), path + ".alternative"));
            node.action().ifPresent(id -> {
                UiActionDefinition action = page.actions().get(id);
                if (action == null) {
                    problems.add(problem(INVALID_ACTION, path, "action", "declared action", id.toString(),
                            "Declare the action in the page namespace."));
                    return;
                }
                boolean overlay = page.type() == UiPageType.HUD || page.type() == UiPageType.NOTIFICATION;
                if ((overlay && action.policy() != UiActionPolicy.LOCAL)
                        || (action.policy() == UiActionPolicy.CONTAINER && page.type() != UiPageType.CONTAINER)) {
                    problems.add(problem(INVALID_ACTION, path, "action", "action allowed for page type",
                            action.policy().name(), "Use a local overlay action or a separately authorized page."));
                }
            });
            for (int i = 0; i < node.children().size() && problems.isEmpty(); i++) {
                checkStructure(node.children().get(i), path + ".children[" + i + "]", depth + 1);
            }
        }

        private void checkProperties(Map<String, UiValue> properties, Map<String, String> bindings, String path) {
            if (properties.size() + bindings.size() > limits.maxProperties()) {
                problems.add(problem(RESOURCE_LIMIT, path, "", "bounded property count",
                        "property limit exceeded", "Reduce the properties or bindings."));
                return;
            }
            try {
                properties.values().forEach(value -> UiValueSchema.checkValueBudget(value, 1, values));
            } catch (IllegalArgumentException tooLarge) {
                problems.add(problem(RESOURCE_LIMIT, path, "", "bounded property values",
                        "value budget exceeded", "Reduce value depth or total entries."));
                return;
            }
            bindings.forEach((property, dataPath) -> {
                if (!page.dataSchema().fields().containsKey(dataPath) || properties.containsKey(property)) {
                    problems.add(problem(INVALID_BINDING, path, property, "one declared data field",
                            dataPath, "Declare the field and choose either a literal property or a binding."));
                }
            });
        }

        private UiNode resolve(UiNode node, String path, boolean root) {
            UiComponentDescriptor descriptor = descriptors.get(node.typeId());
            int before = problems.size();
            if (descriptor != null) {
                checkDescriptor(node, descriptor, path, problems);
            }
            Optional<UiNode> alternative = declaredAlternative(node, path);
            if (problems.size() != before) {
                return null;
            }
            UiNode selected = node;
            if (descriptor == null || !available(descriptor)) {
                Optional<UiNode> replacement = alternative.or(() -> fallback(node, descriptor, path));
                if (replacement.isPresent()) {
                    selected = replacement.orElseThrow();
                    degradations.add(path + " (" + node.id() + "): " + node.typeId() + " -> " + selected.typeId());
                } else if (capabilities.isPresent() && !root && node.fallback() == UiNodeFallback.HIDE
                        && !containsAction(node)) {
                    degradations.add(path + " (" + node.id() + "): hidden " + node.typeId());
                    return null;
                } else {
                    problems.add(problem(COMPONENT_MISSING, path, "typeId", "available component renderer",
                            node.typeId().toString(), "Use a supported component or provide a compatible fallback."));
                    return null;
                }
            }
            List<UiNode> children = new ArrayList<>(node.children().size());
            boolean changed = selected != node;
            for (int i = 0; i < node.children().size(); i++) {
                UiNode child = node.children().get(i);
                UiNode effective = resolve(child, path + ".children[" + i + "]", false);
                changed |= effective != child;
                if (effective != null) {
                    children.add(effective);
                }
            }
            if (changed) {
                return new UiNode(node.id(), selected.typeId(), selected.properties(), selected.bindings(), node.action(),
                        children, UiNodeFallback.REJECT);
            }
            return node;
        }

        private boolean available(UiComponentDescriptor descriptor) {
            return capabilities.isEmpty() || (compatible(capabilities.orElseThrow().get(descriptor.typeId()),
                    descriptor.version()) && capabilities.orElseThrow().keySet().containsAll(descriptor.capabilities()));
        }

        private Optional<UiNode> declaredAlternative(UiNode node, String path) {
            return node.alternative().flatMap(alternative -> {
                UiComponentDescriptor replacement = descriptors.get(alternative.typeId());
                if (replacement == null) {
                    if (capabilities.isEmpty()) {
                        problems.add(problem(COMPONENT_MISSING, path + ".alternative", "typeId", "registered component",
                                alternative.typeId().toString(), "Use a declared replacement component."));
                    }
                    return Optional.empty();
                }
                UiNode candidate = new UiNode(node.id(), alternative.typeId(), alternative.properties(),
                        alternative.bindings(), node.action(), node.children(), UiNodeFallback.REJECT);
                checkDescriptor(candidate, replacement, path + ".alternative", problems);
                return capabilities.isPresent() && available(replacement) ? Optional.of(candidate) : Optional.empty();
            });
        }

        private Optional<UiNode> fallback(UiNode node, UiComponentDescriptor descriptor, String path) {
            if (descriptor == null || node.action().isPresent()) {
                return Optional.empty();
            }
            return descriptor.fallbackType().map(descriptors::get).filter(this::available).flatMap(replacement -> {
                UiNode candidate = new UiNode(node.id(), replacement.typeId(), node.properties(), node.bindings(),
                        node.action(), node.children(), UiNodeFallback.REJECT);
                List<UiPageProblem> errors = new ArrayList<>();
                checkDescriptor(candidate, replacement, path, errors);
                return errors.isEmpty() ? Optional.of(candidate) : Optional.empty();
            });
        }

        private void checkDescriptor(UiNode node, UiComponentDescriptor descriptor, String path,
                                     List<UiPageProblem> errors) {
            if (remote && !descriptor.remoteAllowed()) {
                errors.add(problem(REMOTE_DENIED, path, "typeId", "remote-enabled component",
                        descriptor.typeId().toString(), "Use a component enabled by the client for remote pages."));
                return;
            }
            if (!descriptor.acceptsChildren() && !node.children().isEmpty()) {
                errors.add(problem(INVALID_CHILDREN, path, "children", "leaf component", "children present",
                        "Place the children inside a layout component."));
            }
            if (!descriptor.acceptsAction() && node.action().isPresent()) {
                errors.add(problem(INVALID_ACTION, path, "action", "no action", "action present",
                        "Attach the action to an interactive component."));
            }
            UiBuiltinComponents.inputType(descriptor.typeId()).ifPresent(type ->
                    checkInputAction(node, path, type, errors));
            if (descriptor.typeId().equals(UiBuiltinComponents.id("stack"))
                    && node.children().stream().anyMatch(UiPageValidator::interactive)) {
                errors.add(problem(INVALID_CHILDREN, path, "children", "passive stack children", "interactive child",
                        "Place interactive controls in a column or grid."));
            }
            if ((descriptor.typeId().equals(UiBuiltinComponents.id("list_view"))
                    || descriptor.typeId().equals(UiBuiltinComponents.id("virtual_list")))
                    && node.properties().get("search") instanceof UiValue.Text search
                    && !UiBuiltinComponents.id("search_field").equals(nodeTypes.get(search.value()))) {
                errors.add(problem(INVALID_PROPERTY, path, "search", "stable ID of a search_field",
                        search.value(), "Use a local search field from this fixed tree."));
            }
            if (descriptor.typeId().equals(UiBuiltinComponents.id("container_slot"))
                    && page.type() != UiPageType.CONTAINER) {
                errors.add(problem(INVALID_PROPERTY, path, "typeId", "CONTAINER page", page.type().name(),
                        "Bind native slots through the platform container adapter."));
            }
            node.properties().forEach((property, value) -> checkValue(descriptor, path, property, value, errors));
            node.bindings().forEach((property, dataPath) -> checkBinding(descriptor, path, property, dataPath, errors));
            descriptor.properties().forEach((name, property) -> {
                if (property.required() && property.defaultValue().isEmpty()
                        && !node.properties().containsKey(name) && !node.bindings().containsKey(name)) {
                    errors.add(problem(INVALID_PROPERTY, path, name, property.schema().type().name(), "missing",
                            "Provide the required property or bind a compatible field."));
                }
            });
        }

        private void checkInputAction(UiNode node, String path, UiValueType type, List<UiPageProblem> errors) {
            UiValue field = node.properties().get("payloadField");
            UiActionDefinition action = node.action().map(page.actions()::get).orElse(null);
            if (field == null && (action == null || action.payloadSchema().fields().isEmpty())) return;
            if (field instanceof UiValue.Text name && action != null && action.payloadSchema().fields().size() == 1) {
                UiValueSchema schema = action.payloadSchema().fields().get(name.value());
                if (schema != null && schema.type() == type) return;
            }
            errors.add(problem(INVALID_ACTION, path, "payloadField", "exactly one declared " + type + " field",
                    field instanceof UiValue.Text text ? text.value() : "missing or incompatible field",
                    "Declare a matching action payload field and submit it through the control."));
        }

        private void checkValue(UiComponentDescriptor descriptor, String path, String name, UiValue value,
                                List<UiPageProblem> errors) {
            UiPropertyDescriptor property = descriptor.properties().get(name);
            if (property == null || (remote && !property.remoteConfigurable())) {
                errors.add(problem(INVALID_PROPERTY, path, name, "allowed component property",
                        value.type().name(), "Remove the property or use one declared by the descriptor."));
                return;
            }
            try {
                property.schema().validate(value);
                if (isBuiltinResource(descriptor, name)) {
                    checkResourceReference(((UiValue.Text) value).value(), remote);
                }
            } catch (IllegalArgumentException failure) {
                errors.add(problem(INVALID_PROPERTY, path, name, property.schema().type().name() + " constraints",
                        value.type().name(), "Check the property's type, bounds or resource ID format."));
            }
        }

        private void checkBinding(UiComponentDescriptor descriptor, String path, String name, String field,
                                  List<UiPageProblem> errors) {
            UiPropertyDescriptor property = descriptor.properties().get(name);
            UiValueSchema data = page.dataSchema().fields().get(field);
            boolean resource = isBuiltinResource(descriptor, name);
            boolean resourceChoice = resource && data.type() == UiValueType.ENUM
                    && data.choices().stream().allMatch(choice -> choice.matches("[a-z0-9_-]+"));
            if (property == null || !property.bindable()
                    || (remote && (!property.remoteConfigurable() || resource && !resourceChoice))
                    || (!property.schema().accepts(data) && !resourceChoice
                        && !isTextNumber(descriptor, name, property, data))) {
                errors.add(problem(INVALID_BINDING, path, name,
                        property == null ? "bindable property" : property.schema().type().name() + " compatible field",
                        data.type().name(), "Use a field whose full value range fits the bindable property."));
            }
        }
    }

    private static boolean isBuiltinResource(UiComponentDescriptor descriptor, String property) {
        return (property.equals("icon") && descriptor.typeId().equals(UiBuiltinComponents.id("icon")))
                || (property.equals("image") && descriptor.typeId().equals(UiBuiltinComponents.id("image")));
    }

    private static void checkResourceReference(String value, boolean remote) {
        UiId resource = PageNames.id(UiId.parse(value));
        for (String segment : resource.path().split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid page resource path");
            }
        }
        if (remote && !(resource.namespace().equals("sh_better_ui") && resource.path().matches("[a-z0-9_-]+"))
                && !(resource.path().startsWith("textures/") && resource.path().endsWith(".png"))) {
            throw new IllegalArgumentException("Page resource must refer to a built-in or packaged PNG");
        }
    }

    private static boolean isTextNumber(UiComponentDescriptor descriptor, String name, UiPropertyDescriptor property,
                                        UiValueSchema data) {
        if (!descriptor.typeId().namespace().equals("sh_better_ui") || property.schema().type() != UiValueType.STRING
                || !Set.of("text", "label", "tooltip", "narration").contains(name)) {
            return false;
        }
        return data.type() == UiValueType.INTEGER || data.type() == UiValueType.LONG || data.type() == UiValueType.DOUBLE;
    }

    private static boolean containsAction(UiNode node) {
        return node.action().isPresent() || node.children().stream().anyMatch(UiPageValidator::containsAction);
    }

    private static boolean interactive(UiNode node) {
        String type = node.typeId().path();
        return node.action().isPresent() || UiBuiltinComponents.inputType(node.typeId()).isPresent()
                || node.typeId().namespace().equals("sh_better_ui")
                    && Set.of("scroll_view", "list_view", "virtual_list").contains(type)
                || node.children().stream().anyMatch(UiPageValidator::interactive);
    }

    private static boolean compatible(UiVersion available, UiVersion required) {
        return available != null && available.major() == required.major() && available.compareTo(required) >= 0;
    }

    private static UiPageProblem problem(UiPageProblem.Code code, String location, String property, String expected,
                                         String actual, String suggestion) {
        return new UiPageProblem(code, location, property, expected, actual, suggestion);
    }
}

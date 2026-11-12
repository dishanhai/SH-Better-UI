// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Canonical binary encoding for an immutable page definition. Validate decoded pages before mounting. */
public final class UiPageCodec {
    public static final int MAX_ENCODED_BYTES = 262_144;

    private static final int MAGIC = 0x53485047;
    private static final int MAX_TREE_DEPTH = 32;
    private static final int MAX_TREE_NODES = 1024;

    private UiPageCodec() {}

    public static byte[] encode(UiPageDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        checkVersion(definition.schemaVersion());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                checkSize(1);
                bytes.write(value);
            }

            @Override
            public void write(byte[] source, int offset, int length) throws IOException {
                checkSize(length);
                bytes.write(source, offset, length);
            }

            private void checkSize(int additionalBytes) throws IOException {
                if (additionalBytes > MAX_ENCODED_BYTES - bytes.size()) {
                    throw new IOException("Encoded page exceeds the byte limit");
                }
            }
        })) {
            TreeBudget budget = new TreeBudget();
            output.writeInt(MAGIC);
            writeVersion(output, definition.schemaVersion());
            writeId(output, definition.id());
            output.writeByte(definition.type().ordinal());
            writeString(output, definition.options().title());
            writeMap(output, definition.options().requiredCapabilities(), UiPageCodec::writeId,
                    UiPageCodec::writeVersion);
            writeSchemas(output, definition.dataSchema().fields(), budget.schemas, 1);
            writeMap(output, definition.actions(), UiPageCodec::writeId, (out, action) -> {
                writeId(out, action.id());
                out.writeByte(action.policy().ordinal());
                writeSchemas(out, action.payloadSchema().fields(), budget.schemas, 1);
            });
            writeNode(output, definition.root(), budget, 1);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Cannot encode page definition", failure);
        }
        return bytes.toByteArray();
    }

    public static UiPageDefinition decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Encoded page exceeds the byte limit");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid page definition header");
            }
            TreeBudget budget = new TreeBudget();
            UiVersion version = readVersion(input);
            checkVersion(version);
            UiId id = readId(input);
            UiPageType type = readEnum(input, UiPageType.values());
            UiPageOptions options = new UiPageOptions(readString(input, 256),
                    readMap(input, UiPageCodec::readId, UiPageCodec::readVersion));
            UiDataSchema data = new UiDataSchema(readSchemas(input, budget.schemas, 1));
            Map<UiId, UiActionDefinition> actions = readMap(input, UiPageCodec::readId, in -> new UiActionDefinition(
                    readId(in), readEnum(in, UiActionPolicy.values()),
                    new UiDataSchema(readSchemas(in, budget.schemas, 1))));
            UiNode root = readNode(input, budget, 1);
            if (input.read() != -1) {
                throw new IllegalArgumentException("Trailing page definition data");
            }
            return new UiPageDefinition(id, version, type, root, data, actions, options);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Malformed or truncated page definition", failure);
        }
    }

    /** Writes typed data with collection and nesting limits. The caller supplies a bounded output. */
    public static void writeValues(DataOutput output, Map<String, UiValue> values) throws IOException {
        writeValues(output, values, new UiValueSchema.Budget(), 1);
    }

    /** Reads typed data with collection and nesting limits. The caller supplies a bounded input. */
    public static Map<String, UiValue> readValues(DataInput input) throws IOException {
        return readValues(input, new UiValueSchema.Budget(), 1);
    }

    private static void writeNode(DataOutput output, UiNode node, TreeBudget budget, int depth) throws IOException {
        budget.enter(depth);
        writeString(output, node.id());
        writeId(output, node.typeId());
        writeValues(output, node.properties(), budget.values, 1);
        writeMap(output, node.bindings(), UiPageCodec::writeString, UiPageCodec::writeString);
        writeFlag(output, node.action().isPresent());
        if (node.action().isPresent()) {
            writeId(output, node.action().orElseThrow());
        }
        output.writeByte(node.fallback().ordinal());
        writeFlag(output, node.alternative().isPresent());
        if (node.alternative().isPresent()) {
            UiComponentAlternative alternative = node.alternative().orElseThrow();
            writeId(output, alternative.typeId());
            writeValues(output, alternative.properties(), budget.values, 1);
            writeMap(output, alternative.bindings(), UiPageCodec::writeString, UiPageCodec::writeString);
        }
        writeSize(output, node.children().size());
        for (UiNode child : node.children()) {
            writeNode(output, child, budget, depth + 1);
        }
    }

    private static UiNode readNode(DataInput input, TreeBudget budget, int depth) throws IOException {
        budget.enter(depth);
        String id = readName(input);
        UiId type = readId(input);
        Map<String, UiValue> properties = readValues(input, budget.values, 1);
        Map<String, String> bindings = readMap(input, UiPageCodec::readName, UiPageCodec::readName);
        Optional<UiId> action = readFlag(input) ? Optional.of(readId(input)) : Optional.empty();
        UiNodeFallback fallback = readEnum(input, UiNodeFallback.values());
        Optional<UiComponentAlternative> alternative = readFlag(input)
                ? Optional.of(new UiComponentAlternative(readId(input), readValues(input, budget.values, 1),
                    readMap(input, UiPageCodec::readName, UiPageCodec::readName)))
                : Optional.empty();
        int size = readSize(input);
        List<UiNode> children = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            children.add(readNode(input, budget, depth + 1));
        }
        return new UiNode(id, type, properties, bindings, action, children, fallback, alternative);
    }

    private static void writeValues(DataOutput output, Map<String, UiValue> values, UiValueSchema.Budget budget,
                                    int depth) throws IOException {
        writeMap(output, values, (out, key) -> writeString(out, PageNames.name(key)),
                (out, value) -> writeValue(out, value, budget, depth));
    }

    private static Map<String, UiValue> readValues(DataInput input, UiValueSchema.Budget budget,
                                                    int depth) throws IOException {
        return readMap(input, UiPageCodec::readName, in -> readValue(in, budget, depth));
    }

    private static void writeValue(DataOutput output, UiValue value, UiValueSchema.Budget budget,
                                   int depth) throws IOException {
        budget.enter(depth);
        output.writeByte(value.type().ordinal());
        switch (value.type()) {
            case BOOLEAN -> writeFlag(output, ((UiValue.Bool) value).value());
            case INTEGER -> output.writeInt(((UiValue.Int) value).value());
            case LONG -> output.writeLong(((UiValue.LongInt) value).value());
            case DOUBLE -> output.writeDouble(((UiValue.Decimal) value).value());
            case STRING -> writeString(output, ((UiValue.Text) value).value());
            case ENUM -> writeString(output, ((UiValue.Choice) value).value());
            case LIST -> {
                List<UiValue> items = ((UiValue.Sequence) value).value();
                writeSize(output, items.size());
                for (UiValue item : items) {
                    writeValue(output, item, budget, depth + 1);
                }
            }
            case OBJECT -> writeValues(output, ((UiValue.ObjectValue) value).value(), budget, depth + 1);
            case ITEM_REFERENCE -> {
                UiValue.ItemReference item = (UiValue.ItemReference) value;
                writeId(output, item.itemId());
                output.writeInt(item.count());
            }
        }
    }

    private static UiValue readValue(DataInput input, UiValueSchema.Budget budget, int depth) throws IOException {
        budget.enter(depth);
        return switch (readEnum(input, UiValueType.values())) {
            case BOOLEAN -> new UiValue.Bool(readFlag(input));
            case INTEGER -> new UiValue.Int(input.readInt());
            case LONG -> new UiValue.LongInt(input.readLong());
            case DOUBLE -> new UiValue.Decimal(input.readDouble());
            case STRING -> new UiValue.Text(readString(input, PageNames.MAX_TEXT_LENGTH));
            case ENUM -> new UiValue.Choice(readName(input));
            case LIST -> {
                int size = readSize(input);
                List<UiValue> values = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    values.add(readValue(input, budget, depth + 1));
                }
                yield new UiValue.Sequence(values);
            }
            case OBJECT -> new UiValue.ObjectValue(readValues(input, budget, depth + 1));
            case ITEM_REFERENCE -> new UiValue.ItemReference(readId(input), input.readInt());
        };
    }

    private static void writeSchemas(DataOutput output, Map<String, UiValueSchema> fields,
                                     UiValueSchema.Budget budget, int depth) throws IOException {
        writeMap(output, fields, UiPageCodec::writeString, (out, schema) -> writeSchema(out, schema, budget, depth));
    }

    private static Map<String, UiValueSchema> readSchemas(DataInput input, UiValueSchema.Budget budget,
                                                          int depth) throws IOException {
        return readMap(input, UiPageCodec::readName, in -> readSchema(in, budget, depth));
    }

    private static void writeSchema(DataOutput output, UiValueSchema schema, UiValueSchema.Budget budget,
                                    int depth) throws IOException {
        budget.enter(depth);
        output.writeByte(schema.type().ordinal());
        switch (schema.type()) {
            case BOOLEAN, ITEM_REFERENCE -> { }
            case INTEGER, LONG -> {
                output.writeLong(schema.minimumInteger());
                output.writeLong(schema.maximumInteger());
            }
            case DOUBLE -> {
                output.writeDouble(schema.minimumDecimal());
                output.writeDouble(schema.maximumDecimal());
            }
            case STRING -> output.writeShort(schema.maxSize());
            case ENUM -> {
                writeSize(output, schema.choices().size());
                for (String choice : schema.choices().stream().sorted().toList()) {
                    writeString(output, choice);
                }
            }
            case LIST -> {
                writeSize(output, schema.maxSize());
                writeSchema(output, schema.element().orElseThrow(), budget, depth + 1);
            }
            case OBJECT -> writeSchemas(output, schema.fields(), budget, depth + 1);
        }
    }

    private static UiValueSchema readSchema(DataInput input, UiValueSchema.Budget budget, int depth) throws IOException {
        budget.enter(depth);
        return switch (readEnum(input, UiValueType.values())) {
            case BOOLEAN -> UiValueSchema.bool();
            case INTEGER -> UiValueSchema.integer(readIntegerBound(input), readIntegerBound(input));
            case LONG -> UiValueSchema.longInt(input.readLong(), input.readLong());
            case DOUBLE -> UiValueSchema.decimal(input.readDouble(), input.readDouble());
            case STRING -> UiValueSchema.text(input.readUnsignedShort());
            case ENUM -> {
                int size = readSize(input);
                Set<String> choices = new HashSet<>();
                for (int i = 0; i < size; i++) {
                    if (!choices.add(readName(input))) {
                        throw new IllegalArgumentException("Duplicate page enum choice");
                    }
                }
                yield UiValueSchema.choice(choices);
            }
            case LIST -> {
                int maximum = readSize(input);
                yield UiValueSchema.list(readSchema(input, budget, depth + 1), maximum);
            }
            case OBJECT -> UiValueSchema.object(readSchemas(input, budget, depth + 1));
            case ITEM_REFERENCE -> UiValueSchema.itemReference();
        };
    }

    private static <K extends Comparable<? super K>, V> void writeMap(DataOutput output, Map<K, V> map,
                                                                        Writer<K> key, Writer<V> value)
            throws IOException {
        writeSize(output, map.size());
        for (Map.Entry<K, V> entry : new TreeMap<>(map).entrySet()) {
            key.write(output, entry.getKey());
            value.write(output, entry.getValue());
        }
    }

    private static int readIntegerBound(DataInput input) throws IOException {
        long value = input.readLong();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Page integer bound is out of range");
        }
        return (int) value;
    }

    private static <K, V> Map<K, V> readMap(DataInput input, Reader<K> key, Reader<V> value) throws IOException {
        int size = readSize(input);
        Map<K, V> result = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            K field = key.read(input);
            if (result.containsKey(field)) {
                throw new IllegalArgumentException("Duplicate page map key");
            }
            result.put(field, value.read(input));
        }
        return Map.copyOf(result);
    }

    private static void writeSize(DataOutput output, int size) throws IOException {
        PageNames.size(size, PageNames.MAX_COLLECTION_SIZE);
        output.writeShort(size);
    }

    private static int readSize(DataInput input) throws IOException {
        int size = input.readUnsignedShort();
        PageNames.size(size, PageNames.MAX_COLLECTION_SIZE);
        return size;
    }

    private static void writeId(DataOutput output, UiId id) throws IOException {
        writeString(output, PageNames.id(id).toString());
    }

    private static UiId readId(DataInput input) throws IOException {
        return PageNames.id(UiId.parse(readString(input, PageNames.MAX_NAME_LENGTH)));
    }

    private static String readName(DataInput input) throws IOException {
        return PageNames.name(readString(input, PageNames.MAX_NAME_LENGTH));
    }

    private static void writeVersion(DataOutput output, UiVersion version) throws IOException {
        output.writeInt(version.major());
        output.writeInt(version.minor());
        output.writeInt(version.patch());
    }

    private static UiVersion readVersion(DataInput input) throws IOException {
        return new UiVersion(input.readInt(), input.readInt(), input.readInt());
    }

    private static void checkVersion(UiVersion version) {
        if (version.major() != UiPageDefinition.SCHEMA_VERSION.major()
                || version.compareTo(UiPageDefinition.SCHEMA_VERSION) > 0) {
            throw new IllegalArgumentException("Unsupported page schema version");
        }
    }

    private static void writeString(DataOutput output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInput input, int maxChars) throws IOException {
        int size = input.readUnsignedShort();
        PageNames.size(size, maxChars * 3);
        byte[] bytes = new byte[size];
        input.readFully(bytes);
        return PageNames.text(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(), maxChars);
    }

    private static <T> T readEnum(DataInput input, T[] values) throws IOException {
        int index = input.readUnsignedByte();
        if (index >= values.length) {
            throw new IllegalArgumentException("Unknown page enum value");
        }
        return values[index];
    }

    private static void writeFlag(DataOutput output, boolean flag) throws IOException {
        output.writeByte(flag ? 1 : 0);
    }

    private static boolean readFlag(DataInput input) throws IOException {
        int value = input.readUnsignedByte();
        if (value > 1) {
            throw new IllegalArgumentException("Unknown page boolean value");
        }
        return value != 0;
    }

    @FunctionalInterface
    private interface Writer<T> {
        void write(DataOutput output, T value) throws IOException;
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(DataInput input) throws IOException;
    }

    private static final class TreeBudget {
        private final UiValueSchema.Budget values = new UiValueSchema.Budget();
        private final UiValueSchema.Budget schemas = new UiValueSchema.Budget();
        private int nodes;

        private void enter(int depth) {
            if (depth > MAX_TREE_DEPTH || ++nodes > MAX_TREE_NODES) {
                throw new IllegalArgumentException("Page definition tree exceeds its depth or node limit");
            }
        }
    }
}

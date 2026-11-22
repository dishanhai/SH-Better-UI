// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.muoniumplayer.compat.lwjgl2.Keyboard;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.rendering.ui.AbstractWidget;
import com.muoniumplayer.core.rendering.ui.container.Panel;
import com.muoniumplayer.core.rendering.ui.container.ScrollPanel;
import com.muoniumplayer.core.rendering.ui.widgets.RoundedButtonWidget;
import com.muoniumplayer.core.rendering.ui.widgets.TextFieldWidget;
import com.muoniumplayer.core.screens.ncm.NcmExpressive;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.kernel.internal.music.MusicChatWidgets.*;
import static com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType.*;

/** Provider settings are mounted in the player's content area with the chat draft kept behind them. */
final class MusicChatSettings extends Panel {
    private static final int FIELD_ROW = 32;
    private static final int PROVIDER_COLUMNS = 3;
    private static final int PROVIDER_TOP = 15;
    private static final int PROVIDER_ROW = BUTTON_HEIGHT + GAP;
    private static final int FIELDS_TOP = PROVIDER_TOP + 3 * PROVIDER_ROW + 5;
    private static final int API_ACTION_Y = 3 * FIELD_ROW + 6;
    private static final int PLAN_FIRST_ROW = 23;
    private static final int STATUS_OFFSET = 28;
    private static final int STATUS_HEIGHT = 34;
    private static final int MODEL_ROW = BUTTON_HEIGHT;
    private static final String[][] PROVIDERS = {
            {"provider_openai", "https://api.openai.com/v1"},
            {"provider_deepseek", "https://api.deepseek.com"},
            {"provider_gemini", "https://generativelanguage.googleapis.com/v1beta/openai"},
            {"provider_xai", "https://api.x.ai/v1"},
            {"provider_kimi", "https://api.moonshot.ai/v1"},
            {"provider_zai", "https://api.z.ai/api/paas/v4"},
            {"provider_qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1"}
    };
    private final MusicChatConfig original;
    private List<MusicChatConfig> savedProfiles = List.of();
    private MusicChatConfig selectedApi;
    private final Consumer<MusicChatConfig> apply;
    private final Focus focus = new Focus();
    private final String originalUrl;
    private final String originalModel;
    private String url;
    private String model;
    private String key = "";
    private boolean removeKey;
    private String status = "";
    private List<MusicChatClient.Model> models = List.of();
    private CompletableFuture<List<MusicChatClient.Model>> request;
    private ChatGptLogin.Attempt login;
    private CompletableFuture<Boolean> logout;
    private List<ChatGptLogin.Account> accounts = List.of();
    private boolean subscription;
    private String account = "";
    private String subscriptionModel = "";
    private boolean needsLogin;
    private boolean previewModels;
    private final TextFieldWidget urlField;
    private final TextFieldWidget keyField;
    private final TextFieldWidget modelField;
    private final ScrollPanel content;
    private final Panel form;
    private final Panel apiFields;
    private final Panel planFields;
    private final RoundedButtonWidget clearKeyButton;

    MusicChatSettings(MusicChatConfig original, Consumer<MusicChatConfig> apply, Runnable back) {
        this.original = original;
        this.apply = apply;
        subscription = original != null && original.subscription();
        MusicChatConfig api = subscription ? null : original;
        if (subscription) {
            account = original.account();
            subscriptionModel = original.model();
        }
        try {
            if (api == null) api = MusicChatConfig.loadApi();
            savedProfiles = MusicChatConfig.loadProfiles();
        } catch (IOException | RuntimeException error) {
            status = text("storage_error");
        }
        selectedApi = api;
        originalUrl = api == null ? MusicChatConfig.DEFAULT_URL : api.baseUrl();
        originalModel = api == null ? "" : api.model();
        url = originalUrl;
        model = originalModel;
        setBeforeRenderCallback(() -> setBounds(getParentWidth(), getParentHeight()));
        header(this, focus, text("settings"), back);

        Panel column = column(this, 37, 14, 450);
        content = scroll(column);
        form = new Panel();
        form.setBounds(1, formHeight());
        content.addChild(form);
        content.setBeforeRenderCallback(() -> {
            content.setBounds(column.getWidth(), Math.max(1, column.getHeight() - 50));
            form.setBounds(Math.max(1, content.getWidth() - 8), formHeight());
            if (previewModels) {
                previewModels = false;
                double previewBottom = formHeight() + Math.min(models.size(), 2) * MODEL_ROW;
                content.scrollToOffsetImmediately(Math.max(0, previewBottom - content.getHeight()));
            }
        });

        NcmExpressive.label(form, () -> text("providers"), FontManager.pf14bold, () -> 0, () -> 7,
                form::getWidth, () -> color(PRIMARY_TEXT));
        for (int i = 0; i <= PROVIDERS.length; i++) {
            boolean plan = i == PROVIDERS.length;
            String[] provider = plan ? new String[]{"provider_chatgpt", ""} : PROVIDERS[i];
            int index = i;
            button(form, focus, () -> text(provider[0]), () -> logout == null,
                    () -> plan ? subscription : !subscription && sameEndpoint(url, provider[1]),
                    () -> (index % PROVIDER_COLUMNS) * providerWidth(form) + (index % PROVIDER_COLUMNS) * GAP,
                    () -> PROVIDER_TOP + (index / PROVIDER_COLUMNS) * PROVIDER_ROW,
                    () -> providerWidth(form), () -> selectProvider(plan, provider[1]));
        }

        apiFields = fieldsPanel();
        planFields = fieldsPanel();
        urlField = field(0, "api_url", MusicChatConfig.URL_LIMIT, false, url, () -> text("api_url"),
                this::changeUrl);
        keyField = field(1, "api_key", MusicChatConfig.KEY_LIMIT, true, "", () ->
                text(removeKey || selectedApi == null ? "key_hint" : "key_saved"), value -> {
            key = value;
            removeKey = false;
            credentialsChanged();
        });
        modelField = field(2, "model", MusicChatConfig.MODEL_LIMIT, false, model,
                () -> text("model_hint"), value -> model = value);

        Supplier<String> fetchLabel = () -> request == null ? text("fetch_models") : cancelLabel();
        button(apiFields, focus, fetchLabel, () -> !subscription && !removeKey, () -> true, () -> 0, () -> API_ACTION_Y,
                () -> Math.min(apiFields.getWidth(), buttonWidth(fetchLabel.get())), this::fetch)
                .setTooltip(() -> text("fetch_help"));
        buildPlanFields();
        paragraph(form, () -> status, () -> statusY(), 2);
        paragraph(column, () -> subscription ? text("subscription_storage") : text(removeKey ? "clear_help"
                        : !MusicChatConfig.persistent() ? "storage_session"
                        : MusicChatConfig.windowsStorage() ? "storage_windows" : "storage_posix"),
                () -> column.getHeight() - 47, 2);
        Supplier<String> clearLabel = () -> text(removeKey ? "keep_key" : "clear_key");
        clearKeyButton = button(column, focus, clearLabel, () -> !subscription && selectedApi != null, () -> false, () -> 0,
                () -> column.getHeight() - BUTTON_HEIGHT,
                () -> Math.min(column.getWidth() * .55, buttonWidth(clearLabel.get())), () -> {
                    boolean remove = !removeKey;
                    keyField.setText("");
                    removeKey = remove;
                });
        button(column, focus, () -> text("save"), this::canSave, () -> true,
                () -> column.getWidth() - saveWidth(column), () -> column.getHeight() - BUTTON_HEIGHT,
                () -> saveWidth(column), this::save);
        refreshPanels();
        if (subscription && loadAccounts() && selectedAccount() != null && selectedAccount().authorized()) fetch();
    }

    private static double saveWidth(Panel column) {
        return Math.min(column.getWidth() * .4, buttonWidth(text("save")));
    }

    private static double providerWidth(Panel form) {
        return Math.max(1, (form.getWidth() - (PROVIDER_COLUMNS - 1) * GAP) / PROVIDER_COLUMNS);
    }

    static String channelName(MusicChatConfig profile) {
        if (profile.subscription()) return text("provider_chatgpt");
        for (String[] provider : PROVIDERS) {
            if (sameEndpoint(profile.baseUrl(), provider[1])) return text(provider[0]);
        }
        var endpoint = profile.endpoint();
        return endpoint.getPort() == -1 ? endpoint.getHost() : endpoint.getHost() + ":" + endpoint.getPort();
    }

    private void selectProvider(boolean plan, String endpoint) {
        if (subscription != plan) {
            stopLogin();
            credentialsChanged();
            subscription = plan;
            refreshPanels();
            content.scrollToOffsetImmediately(0);
            if (plan && loadAccounts() && selectedAccount() != null && selectedAccount().authorized()) fetch();
        }
        if (!plan) urlField.setText(endpoint);
    }

    private Panel fieldsPanel() {
        var panel = new Panel();
        form.addChild(panel);
        panel.setBeforeRenderCallback(() -> panel.setBounds(0, FIELDS_TOP, form.getWidth(),
                Math.max(1, statusY() - FIELDS_TOP)));
        return panel;
    }

    private int statusY() {
        int actionY = !subscription ? API_ACTION_Y : hasAuthorization() ? modelTop() : loginTop();
        return FIELDS_TOP + actionY + STATUS_OFFSET;
    }

    private int formHeight() { return statusY() + STATUS_HEIGHT; }
    private int loginTop() { return PLAN_FIRST_ROW + (accounts.isEmpty() ? 0 : PROVIDER_ROW); }
    private int modelTop() { return loginTop() + PROVIDER_ROW; }

    private void refreshPanels() {
        apiFields.setHidden(subscription);
        planFields.setHidden(!subscription);
        clearKeyButton.setHidden(subscription);
        focus.clear();
    }

    private void buildPlanFields() {
        paragraph(planFields, () -> text("subscription_intro"), () -> 0, 2);
        var accountRow = new Panel();
        var modelRow = new Panel();
        planFields.addChild(accountRow, modelRow);
        planFields.setBeforeRenderCallback(() -> {
            planFields.setBounds(0, FIELDS_TOP, form.getWidth(), Math.max(1, statusY() - FIELDS_TOP));
            accountRow.setHidden(accounts.isEmpty()).setBounds(0, PLAN_FIRST_ROW, planFields.getWidth(), BUTTON_HEIGHT);
            modelRow.setHidden(!hasAuthorization()).setBounds(0, modelTop(), planFields.getWidth(), BUTTON_HEIGHT);
        });
        Supplier<String> selected = () -> {
            var active = selectedAccount();
            return active == null ? text("subscription_no_account") : text("subscription_account", active.label());
        };
        button(accountRow, focus, selected, () -> subscription && !authBusy() && accounts.size() > 1,
                () -> false, () -> 0, () -> 0, () -> accountRow.getWidth() * .62 - GAP,
                this::selectNextAccount).setTooltip(() -> text("subscription_switch_help"));

        button(accountRow, focus, () -> text("subscription_add"), () -> subscription && !authBusy() && !accounts.isEmpty(),
                () -> false, () -> accountRow.getWidth() * .62, () -> 0, () -> accountRow.getWidth() * .38,
                () -> startLogin(""));

        Supplier<String> loginLabel = () -> login != null ? cancelLabel()
                : text(logout != null ? "subscription_signing_out"
                : hasAuthorization() ? "subscription_sign_out" : "subscription_continue");
        button(planFields, focus, loginLabel, () -> subscription && logout == null,
                () -> !hasAuthorization(), () -> 0, this::loginTop,
                () -> planFields.getWidth() * .62 - GAP, this::accountAction);
        button(planFields, focus, () -> text(login == null ? "subscription_usage" : "subscription_copy_link"), () -> subscription,
                () -> false, () -> planFields.getWidth() * .62, this::loginTop, () -> planFields.getWidth() * .38,
                this::openUsageOrCopyLogin);

        Supplier<String> label = () -> request != null ? cancelLabel() : text("fetch_models");
        button(modelRow, focus, label, () -> subscription && !authBusy() && hasAuthorization(),
                () -> true, () -> 0, () -> 0,
                () -> Math.min(modelRow.getWidth() * .46, buttonWidth(label.get())), this::fetch);
        NcmExpressive.label(modelRow, () -> subscriptionModel.isEmpty() ? text("subscription_pick_model")
                        : text("subscription_model", chosenModelLabel()),
                FontManager.pf14, () -> modelRow.getWidth() * .46 + GAP, () -> BUTTON_HEIGHT / 2d,
                () -> modelRow.getWidth() * .54 - GAP, () -> color(PRIMARY_TEXT));
    }

    private String chosenModelLabel() {
        for (var candidate : models) if (candidate.id().equals(subscriptionModel)) return candidate.label();
        return subscriptionModel;
    }

    private ChatGptLogin.Account selectedAccount() {
        for (var candidate : accounts) if (candidate.id().equals(account)) return candidate;
        return null;
    }

    private boolean loadAccounts() {
        try {
            accounts = ChatGptLogin.accounts();
            if (account.isEmpty() && !accounts.isEmpty()) chooseAccount(accounts.get(0).id());
            return true;
        } catch (IOException | RuntimeException ignored) {
            setStatus(text("storage_error"));
            return false;
        }
    }

    private void selectNextAccount() {
        int current = accounts.indexOf(selectedAccount());
        chooseAccount(accounts.get((current + 1) % accounts.size()).id());
        if (selectedAccount().authorized()) fetch();
    }

    private void chooseAccount(String id) {
        credentialsChanged();
        if (!id.equals(account)) {
            account = id;
            subscriptionModel = savedProfiles.stream().filter(profile -> profile.account().equals(id))
                    .map(MusicChatConfig::model).findFirst().orElse("");
        }
    }

    private boolean authBusy() { return login != null || logout != null; }
    private boolean hasAuthorization() {
        return !needsLogin && selectedAccount() != null && selectedAccount().authorized();
    }

    private void accountAction() {
        if (login != null) {
            stopLogin();
            setStatus(text("subscription_cancelled"));
        } else if (hasAuthorization()) {
            credentialsChanged();
            logout = ChatGptLogin.signOut(account);
            setStatus(text("subscription_signing_out"));
        } else {
            startLogin(selectedAccount() == null ? "" : account);
        }
    }

    private void startLogin(String id) {
        credentialsChanged();
        try {
            login = ChatGptLogin.start(id, text("subscription_browser_received"), text("subscription_browser_declined"));
        } catch (RuntimeException error) {
            setStatus(MusicChatScreen.responseStatus(error));
            return;
        }
        setStatus(text(ChatGptLogin.openBrowser(login.browser) ? "subscription_wait_browser" : "subscription_manual_browser"));
    }

    private void openUsageOrCopyLogin() {
        if (login != null) {
            try {
                Minecraft.getInstance().keyboardHandler.setClipboard(login.copyLink.toASCIIString());
                setStatus(text("subscription_link_copied"));
            } catch (RuntimeException error) {
                setStatus(text("subscription_copy_failed"));
            }
        } else if (!ChatGptLogin.openBrowser(ChatGptLogin.USAGE)) {
            setStatus(text("subscription_browser_error"));
        }
    }

    private TextFieldWidget field(int row, String label, int limit, boolean secret, String initial,
                                  Supplier<String> hint, Consumer<String> change) {
        int y = row * FIELD_ROW;
        NcmExpressive.label(apiFields, () -> text(label), FontManager.pf14bold, () -> 0, () -> y + 7,
                apiFields::getWidth, () -> color(PRIMARY_TEXT));
        return MusicChatWidgets.field(apiFields, focus, text(label), limit, secret, hint, initial, () -> !subscription,
                () -> 0, () -> y + 12, apiFields::getWidth, change, null);
    }

    private void paragraph(Panel parent, Supplier<String> message, DoubleSupplier top, int limit) {
        var block = new Panel() {
            private String previous;
            private double width = -1;
            private long revision = -1;
            private String[] lines = {};
            @Override public void onRender(double mouseX, double mouseY) {
                String value = message.get();
                var font = FontManager.pf14;
                if (!value.equals(previous) || width != getWidth() || revision != font.metricsRevision()) {
                    previous = value;
                    width = getWidth();
                    revision = font.metricsRevision();
                    lines = NcmExpressive.wrap(font, value, width);
                }
                double y = getY();
                int ink = NcmExpressive.withAlpha(color(SECONDARY_TEXT), getAlpha());
                for (int i = 0; i < Math.min(limit, lines.length); i++) {
                    font.drawString(lines[i], getX(), y, ink);
                    y += font.getFontHeight() + 2;
                }
            }
        };
        parent.addChild(block);
        block.setClickable(false).setBeforeRenderCallback(() ->
                block.setBounds(0, top.getAsDouble(), parent.getWidth() - 2, 28));
    }

    boolean key(int code) {
        if (!subscription && !models.isEmpty() && modelField.isFocused()
                && (code == Keyboard.KEY_UP || code == Keyboard.KEY_DOWN)) {
            int current = -1;
            for (int i = 0; i < models.size(); i++) if (models.get(i).id().equals(model)) current = i;
            int next = Math.max(0, Math.min(models.size() - 1, current + (code == Keyboard.KEY_UP ? -1 : 1)));
            chooseModel(models.get(next));
            reveal(content.getChildren().get(next + 1));
            return true;
        }
        return focus.key(code, this::reveal) || scrollKey(content, code);
    }

    private void reveal(AbstractWidget<?> widget) {
        for (var ancestor = widget.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor != content) continue;
            double above = widget.getY() - content.getY();
            double below = above + widget.getHeight() - content.getHeight();
            if (above < 0 || below > 0) {
                content.scrollToOffsetImmediately(content.targetScrollOffset + (above < 0 ? above : below));
            }
            break;
        }
    }

    private void chooseModel(MusicChatClient.Model selected) {
        if (subscription) subscriptionModel = selected.id();
        else modelField.setText(selected.id());
        MusicTypography.narrate(Component.literal(selected.label()));
    }

    private void rebuildModels() {
        var previous = content.getChildren().subList(1, content.getChildren().size());
        previous.forEach(focus::remove);
        previous.clear();
        for (var candidate : models) {
            var row = scrollButton(content, focus, candidate::label, () -> !authBusy() && request == null,
                    () -> candidate.id().equals(subscription ? subscriptionModel : model),
                    form::getWidth, () -> chooseModel(candidate));
            row.setBounds(1, MODEL_ROW);
            if (subscription) row.setTooltip(candidate::id);
        }
    }

    private void fetch() {
        if (request != null) {
            stopFetch();
            setStatus(text("cancelled"));
            return;
        }
        try {
            if (subscription) {
                if (!hasAuthorization()) return;
                request = MusicChatClient.subscriptionModels(account);
            } else {
                URI endpoint = readEndpoint();
                if (endpoint == null) return;
                String credential = readKey(endpoint);
                if (credential == null) return;
                request = MusicChatClient.models(endpoint, credential);
            }
            setStatus(text("fetching"));
        } catch (RuntimeException error) {
            setStatus(MusicChatScreen.responseStatus(error));
        }
    }

    void tick() {
        if (login != null && login.done()) {
            var completed = login;
            login = null;
            try {
                var signedIn = completed.finish();
                if (!loadAccounts()) return;
                chooseAccount(signedIn.id());
                if (signedIn.authorized()) fetch();
                else setStatus(text("subscription_permission"));
            } catch (IOException ignored) {
                setStatus(text("storage_error"));
            } catch (RuntimeException error) {
                setStatus(MusicChatScreen.responseStatus(error));
            }
        }
        if (logout != null && logout.isDone()) {
            try {
                boolean revoked = logout.join();
                subscriptionModel = "";
                if (loadAccounts()) setStatus(text(revoked ? "subscription_signed_out" : "subscription_revocation_unconfirmed"));
            } catch (RuntimeException error) {
                setStatus(MusicChatScreen.responseStatus(error));
            }
            logout = null;
        }
        if (request == null || !request.isDone()) return;
        try {
            models = request.join();
            if (subscription && models.stream().noneMatch(candidate -> candidate.id().equals(subscriptionModel))) {
                subscriptionModel = "";
            }
            rebuildModels();
            previewModels = !models.isEmpty();
            setStatus(text(models.isEmpty() ? "no_models" : "models_found", models.size()));
        } catch (RuntimeException error) {
            var failure = MusicChatClient.describe(error);
            if (subscription) {
                if (failure.key.equals("subscription_sign_in_required") || failure.key.equals("subscription_permission")) {
                    needsLogin = true;
                    loadAccounts();
                }
            }
            setStatus(text(subscription && failure.key.equals("network_error")
                    ? "subscription_models_network_error" : failure.key, failure.status));
        }
        request = null;
    }

    private void credentialsChanged() {
        stopFetch();
        previewModels = false;
        needsLogin = false;
        if (!models.isEmpty()) {
            models = List.of();
            rebuildModels();
        }
        status = "";
    }

    private void changeUrl(String value) {
        boolean changed = !sameEndpoint(url, value);
        url = value;
        if (!changed) return;
        keyField.setText("");
        key = "";
        removeKey = false;
        selectedApi = savedProfiles.stream().filter(profile -> !profile.subscription() && sameEndpoint(url, profile.baseUrl()))
                .findFirst().orElse(null);
        modelField.setText(selectedApi == null ? "" : selectedApi.model());
        model = modelField.getText();
        credentialsChanged();
    }

    private static boolean sameEndpoint(String first, String second) {
        if (first.equals(second)) return true;
        try {
            return MusicChatConfig.endpoint(first).equals(MusicChatConfig.endpoint(second));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void stopFetch() {
        if (request != null) request.cancel(true);
        request = null;
    }

    private URI readEndpoint() {
        try {
            return MusicChatConfig.endpoint(url);
        } catch (IllegalArgumentException ignored) {
            setStatus(text("invalid_url"));
            return null;
        }
    }

    private String readKey(URI endpoint) {
        if (!key.isBlank() && !removeKey) {
            try {
                return MusicChatConfig.validateKey(key);
            } catch (IllegalArgumentException ignored) {
                setStatus(text("invalid_key"));
                return null;
            }
        }
        if (!removeKey && selectedApi != null && selectedApi.endpoint().equals(endpoint)) return selectedApi.key();
        setStatus(text("key_required"));
        return null;
    }

    private void save() {
        MusicChatConfig result = null;
        if (subscription) {
            if (!canSave()) return;
            result = MusicChatConfig.subscription(account, subscriptionModel);
        } else if (!removeKey) {
            URI endpoint = readEndpoint();
            if (endpoint == null) return;
            String credential = readKey(endpoint);
            if (credential == null) return;
            if (!MusicChatConfig.validModel(model.strip()) || model.strip().contains(credential)) {
                setStatus(text("invalid_model"));
                return;
            }
            result = new MusicChatConfig(endpoint, model, credential);
        }
        try {
            if (removeKey && !subscription) result = MusicChatConfig.removeApi(selectedApi);
            else MusicChatConfig.save(result);
        } catch (IOException | RuntimeException ignored) {
            setStatus(text("storage_error"));
            return;
        }
        apply.accept(result);
    }

    private void setStatus(String message) {
        status = message;
        MusicTypography.narrate(Component.literal(message));
    }

    private boolean canSave() {
        return !authBusy() && request == null && dirty() && (!subscription || hasAuthorization()
                && models.stream().anyMatch(candidate -> candidate.id().equals(subscriptionModel)));
    }

    boolean dirty() {
        boolean wasSubscription = original != null && original.subscription();
        if (subscription != wasSubscription) return true;
        return subscription ? !account.equals(original.account()) || !subscriptionModel.equals(original.model())
                : removeKey || !key.isEmpty() || !url.equals(originalUrl) || !model.equals(originalModel);
    }

    private void stopLogin() {
        if (login == null) return;
        login.close();
        login = null;
    }

    void suspend() {
        if (login != null) {
            stopLogin();
            status = text("subscription_cancelled");
        }
        if (request != null) {
            stopFetch();
            status = text("cancelled");
        }
        focus.clear();
    }

    void dispose() {
        suspend();
        keyField.setText("");
    }
}

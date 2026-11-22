// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.panel.MusicChatPlatform;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.settings.AssistantSettings;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.rendering.FoliaLucideIcons;
import com.muoniumplayer.core.rendering.GlassLook;
import com.muoniumplayer.core.rendering.ui.AbstractWidget;
import com.muoniumplayer.core.rendering.ui.container.Panel;
import com.muoniumplayer.core.rendering.ui.container.ScrollPanel;
import com.muoniumplayer.core.rendering.ui.widgets.TextFieldWidget;
import com.muoniumplayer.core.screens.ncm.ConfirmationOverlay;
import com.muoniumplayer.core.screens.ncm.NCMPanel;
import com.muoniumplayer.core.screens.ncm.NCMScreen;
import com.muoniumplayer.core.screens.ncm.NcmExpressive;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.kernel.internal.music.MusicChatWidgets.*;
import static com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType.*;

/** UUK content page inside the player's navigation and transport shell. */
final class MusicChatScreen extends NCMPanel {
    private final NCMScreen player;
    private final Supplier<MusicChatClient.Song> playback;
    private final List<MusicChatClient.Turn> history = new ArrayList<>();
    private final Map<String, MusicChatHistory.Entry> archived = new HashMap<>();
    private final Map<String, String> drafts = new HashMap<>();
    private MusicChatClient.Turn typing;
    private long typingSince;
    private String conversationId = MusicChatHistory.newId();
    private MusicChatHistoryPanel historyPanel;
    private boolean archiveFailed;
    private MusicChatConfig config;
    private CompletableFuture<MusicChatClient.Reply> request;
    private MusicChatPlayback.Context actionContext;
    private CompletableFuture<MusicChatPlayback.Search> search;
    private CompletableFuture<MusicChatPlayback.Resolution> selectedSongs;
    private MusicChatActions.Purpose selectedPurpose;
    private MusicChatChoices choices;
    private MusicChatActions.Command songCommand;
    private String settingFacts;
    private Panel chat;
    private Focus focus;
    private MusicChatSettings settings;
    private ConfirmationOverlay discard;
    private TextFieldWidget input;
    private ScrollPanel transcript;
    private ScrollPanel channels;
    private String status = "";
    private String question;
    private boolean attachSong;
    private boolean controlEnabled;
    private boolean actionQueued;
    private int contextStart;
    private boolean refreshTranscript;
    private boolean followTranscript;
    private boolean loadFailed;
    private long fontRevision = -1;
    private double wrappedWidth = -1;

    MusicChatScreen(NCMScreen player, Supplier<MusicChatClient.Song> playback) {
        this.player = player;
        this.playback = playback;
    }

    @Override
    public void onInit() {
        if (chat != null) return;
        try {
            config = MusicChatConfig.load();
        } catch (IOException | RuntimeException ignored) {
            loadFailed = true;
        }
        chat = new Panel();
        focus = new Focus();
        addChild(chat);
        chat.setBeforeRenderCallback(() -> chat.setBounds(getWidth(), getHeight()));
        header(chat, focus, text("title"), player::navigateBack);
        historyButton(chat, focus, this::historyOpen, this::toggleHistory);
        historyPanel = new MusicChatHistoryPanel(focus, () -> conversationId, this::restoreConversation, this::deleteConversation);
        chat.addChild(historyPanel);
        historyPanel.setHidden(true).setBeforeRenderCallback(() -> historyPanel.setBounds(INSET + 6, 72,
                historyWidth(), Math.max(1, chat.getHeight() - 86)));

        var conversation = new Panel();
        chat.addChild(conversation);
        conversation.setBeforeRenderCallback(() -> {
            double left = historyOpen() ? INSET + 6 + historyWidth() : chat.getWidth() < 584 ? 42 : 0;
            conversation.setBounds(left, 0, Math.max(1, chat.getWidth() - left), chat.getHeight());
        });

        Panel column = column(conversation, 44, 14, 500);
        Supplier<String> attach = () -> text(attachSong ? "attach_on" : "attach_off");
        Supplier<String> controls = () -> text(controlEnabled ? "control_on" : "control_off");
        Supplier<String> channel = () -> text("channel");
        Supplier<String> settingsLabel = () -> text("settings");
        Supplier<String> resetLabel = () -> text("new_chat");
        button(column, focus, attach, () -> true, () -> attachSong, () -> 0, () -> 0,
                () -> toolbarWidth(column, attach), this::toggleSong)
                .setTooltip(() -> text("attach_help"));
        button(column, focus, controls, () -> config != null, () -> controlEnabled,
                () -> toolbarWidth(column, attach) + GAP, () -> 0,
                () -> toolbarWidth(column, controls), this::toggleControl)
                .setTooltip(() -> text("control_help"));
        button(column, focus, channel, () -> !busy() && choices == null, this::channelsOpen,
                () -> toolbarWidth(column, attach) + toolbarWidth(column, controls) + 2 * GAP, () -> 0,
                () -> toolbarWidth(column, channel), this::toggleChannels)
                .setTooltip(() -> config == null ? text("channel_help") : channelLabel(config));
        button(column, focus, resetLabel, () -> !busy() && choices == null
                        && (!history.isEmpty() || input != null && !input.getText().isBlank()), () -> false,
                () -> column.getWidth() - toolbarWidth(column, settingsLabel) - GAP - toolbarWidth(column, resetLabel),
                () -> 0, () -> toolbarWidth(column, resetLabel), () -> restoreConversation(null));
        button(column, focus, settingsLabel, () -> true, () -> config == null,
                () -> column.getWidth() - toolbarWidth(column, settingsLabel), () -> 0,
                () -> toolbarWidth(column, settingsLabel), this::openSettings);

        channels = scroll(column);
        channels.setHidden(true).setBeforeRenderCallback(() ->
                channels.setBounds(0, 27, column.getWidth(), channelHeight(column)));
        transcript = scroll(column).setSpacing(8);
        transcript.setBeforeRenderCallback(() -> {
            double top = 32 + channelHeight(column);
            transcript.setBounds(0, top, column.getWidth(), Math.max(1, column.getHeight() - top - 48));
            rebuildMessages();
        });
        Panel empty = new Panel() {
            @Override public void onRender(double mouseX, double mouseY) {
                if (!history.isEmpty() || question != null || transcript.getHeight() < 64) return;
                FoliaLucideIcons.draw(FoliaLucideIcons.SPARKLE, getX() + getWidth() / 2,
                        getY() + 11, 20, NcmExpressive.withAlpha(color(ACCENT), getAlpha()));
                String hint = text(loadFailed ? "storage_error" : config == null ? "needs_config" : "ready");
                var font = FontManager.pf14;
                double y = getY() + 30;
                for (String line : NcmExpressive.wrap(font, hint, getWidth())) {
                    font.drawString(line, getX() + (getWidth() - font.getStringWidthD(line)) / 2,
                            y, NcmExpressive.withAlpha(color(SECONDARY_TEXT), getAlpha()));
                    y += font.getFontHeight() + 3;
                }
            }
        };
        column.addChild(empty);
        empty.setClickable(false).setBeforeRenderCallback(() ->
                empty.setBounds(0, transcript.getRelativeY() + Math.max(3, (transcript.getHeight() - 62) / 2),
                        column.getWidth(), 56));
        activityLabel(column, () -> status.isBlank() && config != null ? channelLabel(config) : status,
                this::busy, () -> column.getHeight() - 35);

        Supplier<String> send = () -> busy() ? cancelLabel() : text("send");
        DoubleSupplier sendWidth = () -> Math.min(column.getWidth() / 3, buttonWidth(send.get()));
        input = field(column, focus, text("question"), MusicChatClient.QUESTION_LIMIT, false,
                () -> text("question"), "", () -> config != null && !busy() && choices == null && !channelsOpen(),
                () -> 0, () -> column.getHeight() - BUTTON_HEIGHT,
                () -> column.getWidth() - sendWidth.getAsDouble() - GAP, value -> {}, this::send);
        button(column, focus, send, () -> busy() || choices == null && config != null
                        && !channelsOpen() && !input.getText().isBlank(),
                () -> !busy(), () -> column.getWidth() - sendWidth.getAsDouble(),
                () -> column.getHeight() - BUTTON_HEIGHT, sendWidth, () -> {
                    if (busy()) cancel();
                    else send();
                });
        refreshTranscript = true;
    }

    private static double toolbarWidth(Panel column, Supplier<String> title) {
        return Math.max(1, Math.min((column.getWidth() - 4 * GAP) / 5, buttonWidth(title.get())));
    }

    private boolean busy() { return request != null || search != null || selectedSongs != null; }

    @Override
    public void renderWidget(double mouseX, double mouseY, int wheel) {
        onInit();
        if (player.isContentPanelActive(this)) {
            if (settings == null) tick();
            else settings.tick();
        }
        super.renderWidget(mouseX, mouseY, wheel);
    }

    @Override
    public boolean onKeyTypedReceived(char character, int key) {
        if (settings != null && settings.key(key)) return true;
        if (settings == null && focus != null && (focus.key(key, this::reveal)
                || channelsOpen() && scrollKey(channels, key)
                || historyOpen() && historyPanel.scrollKey(key) || scrollKey(transcript, key))) return true;
        return super.onKeyTypedReceived(character, key);
    }

    private void toggleSong() {
        attachSong = !attachSong;
        if (!attachSong) {
            contextStart = history.size() + (busy() || choices != null ? 1 : 0);
            saveConversation();
        }
        MusicTypography.narrate(Component.literal(text(attachSong ? "attach_on" : "attach_off")));
    }

    private void toggleControl() {
        controlEnabled = !controlEnabled;
        if (!controlEnabled && actionContext != null) cancel();
        MusicTypography.narrate(Component.literal(text(controlEnabled ? "control_on" : "control_off")));
    }

    private boolean historyOpen() { return historyPanel != null && !historyPanel.isHidden(); }

    private boolean channelsOpen() { return channels != null && !channels.isHidden(); }

    private double channelHeight(Panel column) {
        if (!channelsOpen()) return 0;
        return Math.min(Math.max(BUTTON_HEIGHT, column.getHeight() / 3),
                Math.min(4, channels.getChildren().size()) * BUTTON_HEIGHT);
    }

    private void toggleChannels() {
        if (channelsOpen()) {
            closeChannels();
            return;
        }
        if (busy() || choices != null) return;
        try {
            var profiles = MusicChatConfig.loadProfiles();
            var accounts = new HashMap<String, ChatGptLogin.Account>();
            if (profiles.stream().anyMatch(MusicChatConfig::subscription)) {
                try {
                    for (var account : ChatGptLogin.accounts()) accounts.put(account.id(), account);
                } catch (IOException | RuntimeException ignored) {
                    setStatus(text("storage_error"));
                }
            }
            channels.setHidden(false);
            for (var profile : profiles) {
                var account = accounts.get(profile.account());
                if (profile.subscription() && (account == null || !account.authorized())) continue;
                String label = channelLabel(profile);
                if (account != null) label = text("channel_account", label, account.label());
                channelRow(channels, focus, label, this::channelsOpen, () -> profile.equals(config), () -> pickChannel(profile));
            }
            if (channels.getChildren().isEmpty()) {
                channelRow(channels, focus, text("channel_setup"), this::channelsOpen, () -> false, this::openSettings);
            }
            channels.scrollToOffsetImmediately(0);
        } catch (IOException | RuntimeException ignored) {
            closeChannels();
            setStatus(text("storage_error"));
        }
    }

    private void closeChannels() {
        if (!channelsOpen()) return;
        channels.setHidden(true);
        for (var widget : channels.getChildren()) focus.remove(widget);
        channels.getChildren().clear();
    }

    private void pickChannel(MusicChatConfig profile) {
        closeChannels();
        if (Objects.equals(config, profile)) return;
        try {
            MusicChatConfig.select(profile);
            applyChannel(profile);
            MusicTypography.narrate(Component.literal(channelLabel(profile)));
        } catch (IOException | RuntimeException ignored) {
            setStatus(text("storage_error"));
        }
    }

    private static String channelLabel(MusicChatConfig profile) {
        return text("channel_model", MusicChatSettings.channelName(profile), profile.model());
    }

    private double historyWidth() { return Math.min(145, Math.max(65, chat.getWidth() * .31)); }

    private void toggleHistory() {
        if (historyOpen()) {
            historyPanel.setHidden(true);
            focus.clear();
            return;
        }
        try {
            var entries = MusicChatHistory.LOCAL.load();
            if (!archiveFailed) archived.clear();
            for (var entry : entries) archived.putIfAbsent(entry.id(), entry);
            archiveFailed = false;
        } catch (IOException | RuntimeException error) {
            archiveFailed = true;
        }
        historyPanel.setHidden(false);
        updateArchive();
    }

    private void updateArchive() {
        if (!historyOpen()) return;
        historyPanel.update(archived.values().stream().sorted(MusicChatHistory.RECENT_FIRST).toList(), archiveFailed);
    }

    private void saveConversation() {
        if (history.isEmpty()) return;
        var previous = archived.get(conversationId);
        String title = previous == null ? MusicChatHistory.title(history.get(0).question()) : previous.title();
        var entry = new MusicChatHistory.Entry(conversationId, title, MusicChatHistory.providerId(config),
                System.currentTimeMillis(), Math.min(contextStart, history.size()), history);
        archived.put(conversationId, entry);
        try {
            MusicChatHistory.LOCAL.save(entry);
            archiveFailed = false;
        } catch (IOException | RuntimeException error) {
            archiveFailed = true;
        }
        updateArchive();
    }

    private void restoreConversation(MusicChatHistory.Entry entry) {
        if (entry != null && conversationId.equals(entry.id())) return;
        closeChannels();
        cancel();
        if (!input.getText().isBlank()) drafts.put(conversationId, input.getText());
        else drafts.remove(conversationId);
        history.clear();
        typing = null;
        conversationId = entry == null ? MusicChatHistory.newId() : entry.id();
        if (entry != null) history.addAll(entry.turns());
        contextStart = entry == null ? 0 : entry.contextFor(config);
        input.setText(drafts.getOrDefault(conversationId, ""));
        status = "";
        focus.clear();
        refreshTranscript = followTranscript = true;
    }

    private void deleteConversation() {
        var entry = archived.get(conversationId);
        if (entry == null) return;
        String name = entry.title().isBlank() ? text("history_untitled") : entry.title();
        player.openConfirmation(text("history_delete"), text("history_delete_detail", name), text("history_delete_confirm"), () -> {
            try {
                MusicChatHistory.LOCAL.remove(entry.id());
                archived.remove(entry.id());
                if (conversationId.equals(entry.id())) restoreConversation(null);
                drafts.remove(entry.id());
                archiveFailed = false;
            } catch (IOException | RuntimeException error) {
                archiveFailed = true;
            }
            updateArchive();
        });
        discard = player.confirmationOverlay;
    }

    private void openSettings() {
        closeChannels();
        cancel();
        focus.clear();
        settings = new MusicChatSettings(config, this::configure, this::closeSettings);
        getChildren().clear();
        addChild(settings);
        MusicTypography.narrate(Component.literal(text("settings")));
    }

    boolean closeSettings() {
        if (settings == null) {
            if (channelsOpen()) {
                closeChannels();
                return true;
            }
            if (!historyOpen()) return false;
            toggleHistory();
            return true;
        }
        if (settings.dirty()) {
            settings.suspend();
            player.openConfirmation(text("discard_title"), text("discard_details"), text("discard"), this::showChat);
            discard = player.confirmationOverlay;
        } else {
            showChat();
        }
        return true;
    }

    private void showChat() {
        settings.dispose();
        settings = null;
        getChildren().clear();
        addChild(chat);
    }

    private void configure(MusicChatConfig value) {
        applyChannel(value);
        showChat();
    }

    private void applyChannel(MusicChatConfig value) {
        if (!Objects.equals(config, value)) {
            config = value;
            attachSong = false;
            controlEnabled = false;
            contextStart = history.size();
            saveConversation();
        }
        loadFailed = false;
        status = "";
    }

    void suspend() {
        cancel();
        closeChannels();
        typing = null;
        if (focus != null) focus.clear();
        if (settings != null) settings.suspend();
    }

    void resetSession() {
        suspend();
        if (settings != null) settings.dispose();
        if (discard == player.confirmationOverlay) player.confirmationOverlay = null;
        discard = null;
        settings = null;
        chat = null;
        focus = null;
        input = null;
        transcript = null;
        channels = null;
        historyPanel = null;
        config = null;
        attachSong = false;
        controlEnabled = false;
        loadFailed = false;
        history.clear();
        drafts.clear();
        archived.clear();
        conversationId = MusicChatHistory.newId();
        contextStart = 0;
        status = "";
        getChildren().clear();
    }

    private void send() {
        String draft = input.getText();
        if (config == null || busy() || choices != null || channelsOpen() || draft.isBlank()) return;
        typing = null;
        question = MusicChatClient.redact(draft.strip(), config.key());
        try {
            actionContext = controlEnabled ? MusicChatPlayback.Context.capture() : null;
            requestReply();
            input.setText("");
            refreshTranscript = followTranscript = true;
        } catch (RuntimeException ignored) {
            setStatus(text("network_error"));
            question = null;
            actionContext = null;
        }
    }

    private void requestReply() {
        request = MusicChatClient.ask(config, history.subList(contextStart, history.size()),
                question, attachSong, attachSong ? playback.get() : null, actionContext != null,
                actionContext == null ? List.of() : AssistantSettings.ids());
        setStatus(text("waiting"));
    }

    private void tick() {
        if (selectedSongs != null) {
            if (!selectedSongs.isDone()) return;
            if (!canApplyAction()) { cancel(); return; }
            try {
                finishAction(MusicChatPlayback.applySelection(selectedPurpose, actionContext, selectedSongs.join()));
            } catch (RuntimeException error) {
                finishAction(text("action_search_error"));
            }
            return;
        }
        if (search != null) {
            if (!search.isDone()) return;
            if (!canApplyAction()) {
                cancel();
                return;
            }
            try {
                var result = search.join();
                if (!actionContext.trackChanged() && !actionContext.sourceChanged() && result.alternatives().size() > 1) {
                    var purpose = songCommand.action() == MusicChatActions.Action.PLAY
                            ? MusicChatActions.Purpose.PLAY : MusicChatActions.Purpose.QUEUE;
                    showChoices(new MusicChatActions.Question(text("choice_ambiguous", songCommand.target()),
                            false, purpose, result.alternatives()));
                } else finishAction(MusicChatPlayback.play(songCommand, actionContext, result.match()));
            } catch (CompletionException ignored) {
                finishAction(text("action_search_error"));
            } catch (RuntimeException ignored) {
                finishAction(text("action_error"));
            }
            return;
        }
        if (actionQueued || request == null || !request.isDone()) return;
        try {
            var reply = request.join();
            if (reply.question() != null) {
                if (canApplyAction()) showChoices(reply.question());
                else cancel();
            } else if (reply.command() != null) {
                queueAction(reply.command());
            } else {
                String prefix = settingFacts == null ? "" : settingFacts + "\n\n";
                completeTurn(prefix + reply.text(), text("response_ready"), prefix.length());
            }
            return;
        } catch (RuntimeException error) {
            setStatus(responseStatus(error));
        }
        if (settingFacts != null) completeTurn(settingFacts, status);
        else finishRequest();
    }

    private void queueAction(MusicChatActions.Command command) {
        var expected = request;
        actionQueued = true;
        // Navigation and modal actions can mutate the player tree. Run outside the render traversal.
        MusicChatPlatform.enqueue(() -> {
            if (request != expected) return;
            actionQueued = false;
            if (!canApplyAction()) {
                cancel();
                return;
            }
            try {
                apply(command);
            } catch (RuntimeException ignored) {
                if (question != null) finishAction(settingFacts == null ? text("action_error") : settingFacts);
            }
        });
    }

    private boolean canApplyAction() {
        return controlEnabled && actionContext != null && player.isContentPanelActive(this) && !player.isModalOverlayActive()
                && player.musicLyricsPanel == null;
    }

    private void showChoices(MusicChatActions.Question prompt) {
        request = null;
        search = null;
        songCommand = null;
        String label = text(switch (prompt.purpose()) {
            case PLAY -> "choice_play";
            case QUEUE -> "choice_queue";
            case CLARIFY -> "choice_confirm";
        });
        choices = new MusicChatChoices(prompt, focus, label, this::confirmChoices, this::cancel);
        status = "";
        refreshTranscript = followTranscript = true;
        MusicTypography.narrate(Component.literal(prompt.prompt()));
    }

    private void confirmChoices(List<MusicChatActions.Option> selected) {
        if (choices == null || selected.isEmpty()) return;
        if (!canApplyAction()) { cancel(); return; }
        var purpose = choices.question().purpose();
        remember(choices.transcriptText(), -1);
        removeChoices();
        var names = selected.stream().map(MusicChatChoices::optionText).toList();
        question = text("choice_response", String.join(" / ", names));
        refreshTranscript = followTranscript = true;
        if (purpose == MusicChatActions.Purpose.CLARIFY) {
            try {
                actionContext = MusicChatPlayback.Context.capture();
                requestReply();
            } catch (RuntimeException error) {
                setStatus(responseStatus(error));
                finishRequest();
            }
        } else if (actionContext.trackChanged() || actionContext.sourceChanged()) {
            finishAction(text("action_stale"));
        } else if (!MusicChatPlayback.available()) {
            finishAction(text("action_unavailable"));
        } else {
            selectedPurpose = purpose;
            selectedSongs = MusicChatPlayback.resolve(selected);
            setStatus(text("action_searching"));
        }
    }

    private void removeChoices() {
        if (choices == null) return;
        choices.release();
        choices = null;
    }

    private void apply(MusicChatActions.Command command) {
        if (command.needsSearch()) {
            if (!MusicChatPlayback.available()) finishAction(text("action_unavailable"));
            else if (actionContext.trackChanged() || actionContext.sourceChanged()) finishAction(text("action_stale"));
            else {
                search = MusicChatPlayback.search(command);
                songCommand = command;
                request = null;
                setStatus(text("action_searching"));
            }
            return;
        }
        String result;
        try {
            result = switch (command.action()) {
                case LIST_SETTINGS -> AssistantSettings.list();
                case READ_SETTING -> AssistantSettings.read(command.target());
                case SET_SETTING -> AssistantSettings.write(command.target(), command.value());
                case OPEN_LYRICS -> MusicChatPlayerExtras.openLyrics(player);
                case TRANSLATED_LYRICS -> MusicChatPlayerExtras.setTranslatedLyrics(command.value().equals("on"));
                case FLOATING_LYRICS -> MusicChatPlayerExtras.setFloatingLyrics(command.value().equals("on"));
                case MUSIC_HUD -> MusicChatPlayerExtras.setMusicHud(command.value().equals("on"));
                case THEME -> MusicChatPlayerExtras.chooseTheme(command.target());
                case OPEN_CONTROL_CENTER -> MusicChatPlayerExtras.openControlCenter();
                case FAVORITE -> actionContext.trackChanged() ? text("action_stale")
                        : MusicChatPlayerExtras.favoriteCurrent(player, command.value().equals("on"));
                case ADD_TO_PLAYLIST -> actionContext.trackChanged() ? text("action_stale")
                        : MusicChatPlayerExtras.addCurrentToPlaylist(player, command.target());
                default -> MusicChatPlayback.control(command, actionContext);
            };
        } catch (RuntimeException ignored) {
            result = text("action_error");
        }
        // Opening another screen may already have cancelled and reset the chat.
        if (question == null) return;
        if (command.explainsSettings()) {
            settingFacts = MusicChatClient.redact(result, config.key());
            request = MusicChatClient.explainSettings(config, question, settingFacts);
            setStatus(text("action_explaining"));
        } else {
            finishAction(result);
        }
    }

    private void finishAction(String result) {
        String safeResult = MusicChatClient.redact(result, config.key());
        completeTurn(text("action_result", safeResult), safeResult);
    }

    private void completeTurn(String answer, String outcome) {
        completeTurn(answer, outcome, -1);
    }

    private void completeTurn(String answer, String outcome, int markdownStart) {
        var turn = remember(answer, markdownStart);
        if (markdownStart >= 0 && !UiPreferences.configured().reducedMotion()) {
            typing = turn;
            typingSince = System.nanoTime();
        }
        setStatus(outcome);
        question = null;
        finishRequest();
    }

    private MusicChatClient.Turn remember(String answer, int markdownStart) {
        var turn = new MusicChatClient.Turn(question, answer, markdownStart);
        history.add(turn);
        if (history.size() > MusicChatClient.HISTORY_LIMIT) {
            history.remove(0);
            contextStart = Math.max(0, contextStart - 1);
        }
        saveConversation();
        return turn;
    }

    static String responseStatus(Throwable error) {
        var failure = MusicChatClient.describe(error);
        return text(failure.key, failure.status);
    }

    private void setStatus(String value) {
        status = value;
        MusicTypography.narrate(Component.literal(status));
    }

    private void cancel() {
        if (!busy() && choices == null) return;
        if (request != null) request.cancel(true);
        if (search != null) search.cancel(true);
        if (selectedSongs != null) selectedSongs.cancel(true);
        status = text("cancelled");
        finishRequest();
    }

    private void finishRequest() {
        request = null;
        actionContext = null;
        actionQueued = false;
        search = null;
        selectedSongs = null;
        selectedPurpose = null;
        removeChoices();
        songCommand = null;
        settingFacts = null;
        contextStart = Math.min(contextStart, history.size());
        if (question != null && input.getText().isEmpty()) input.setText(question);
        question = null;
        refreshTranscript = followTranscript = true;
    }

    private void rebuildMessages() {
        double width = Math.max(1, transcript.getWidth() - 7);
        long revision = FontManager.pf14.metricsRevision();
        if (!refreshTranscript && wrappedWidth == width && fontRevision == revision) return;
        refreshTranscript = false;
        wrappedWidth = width;
        fontRevision = revision;
        transcript.getChildren().clear();
        for (var turn : history) {
            message(turn.question(), true, width, -1, null);
            message(turn.answer(), false, width, turn.markdownStart(), turn == typing ? turn : null);
        }
        if (question != null) message(question, true, width, -1, null);
        if (choices != null) {
            choices.resize(width * .9);
            transcript.addChild(choices);
        }
        if (followTranscript) {
            double top = 0;
            var children = transcript.getChildren();
            var latest = children.isEmpty() ? null : children.get(children.size() - 1);
            if ((choices != null || typing != null) && latest != null && latest.getHeight() > transcript.getHeight()) {
                for (var child : transcript.getChildren()) {
                    if (child == latest) break;
                    top += child.getHeight() + transcript.getSpacing();
                }
            } else top = Double.MAX_VALUE;
            transcript.scrollToOffsetImmediately(top);
        }
        followTranscript = false;
    }

    private void reveal(AbstractWidget<?> widget) {
        if (historyOpen() && historyPanel.reveal(widget)) return;
        for (var parent = widget.getParent(); parent != null; parent = parent.getParent()) {
            if (parent != transcript && parent != channels) continue;
            var scroll = (ScrollPanel) parent;
            double above = widget.getY() - scroll.getY();
            double below = above + widget.getHeight() - scroll.getHeight();
            if (above < 0 || below > 0) {
                scroll.scrollToOffsetImmediately(scroll.targetScrollOffset + (above < 0 ? above : below));
            }
            return;
        }
    }

    private void message(String message, boolean user, double width, int markdownStart, MusicChatClient.Turn animated) {
        double maxCardWidth = width * (user ? .76 : .9);
        var font = FontManager.pf14;
        double available = Math.max(1, maxCardWidth - 20);
        var lines = new ArrayList<MusicChatMarkdown.Line>();
        if (markdownStart < 0) lines.addAll(MusicChatMarkdown.plain(message, available));
        else if (markdownStart > 0) lines.addAll(MusicChatMarkdown.plain(message.substring(0, markdownStart), available));
        int literalLines = lines.size();
        if (markdownStart >= 0) lines.addAll(MusicChatMarkdown.layout(message.substring(markdownStart), available));
        int animatedLength = animated == null ? 0 : lines.subList(literalLines, lines.size()).stream()
                .mapToInt(MusicChatMarkdown.Line::length).sum();
        double textWidth = 72;
        for (var line : lines) textWidth = Math.max(textWidth, line.width());
        double cardWidth = Math.min(maxCardWidth, textWidth + 20);
        double stride = font.getFontHeight() + 4;
        var card = new Panel() {
            @Override public void onRender(double mouseX, double mouseY) {
                int tint = color(user ? ELEMENT_HOVER : ELEMENT_BACKGROUND);
                roundedRect(getX(), getY(), getWidth(), getHeight(), 7,
                        NcmExpressive.withAlpha(tint, getAlpha() * GlassLook.washAlpha(GlassLook.Surface.PLAYER, .8f)));
                double x = getX() + 10;
                FontManager.pf14bold.drawString(text(user ? "you" : "assistant"), x, getY() + 7,
                        NcmExpressive.withAlpha(color(user ? PRIMARY_TEXT : ACCENT), getAlpha()));
                double y = getY() + 22;
                int ink = NcmExpressive.withAlpha(color(PRIMARY_TEXT), getAlpha());
                int muted = NcmExpressive.withAlpha(color(SECONDARY_TEXT), getAlpha());
                int remaining = visibleGraphemes(animated, animatedLength);
                for (int index = 0; index < lines.size(); index++) {
                    var line = lines.get(index);
                    int budget = index < literalLines ? Integer.MAX_VALUE : remaining;
                    if (y + stride >= transcript.getY() && y < transcript.getY() + transcript.getHeight()) {
                        line.draw(x, y, ink, muted, budget);
                    }
                    if (index >= literalLines && remaining != Integer.MAX_VALUE) remaining -= line.length();
                    y += stride;
                }
            }
        };
        transcript.addChild(card);
        card.setClickable(false).setBounds(user ? width - cardWidth : 0, 0, cardWidth, 28 + lines.size() * stride);
    }

    private int visibleGraphemes(MusicChatClient.Turn animated, int length) {
        if (animated == null || typing != animated) return Integer.MAX_VALUE;
        long elapsed = System.nanoTime() - typingSince;
        long duration = Math.max(220_000_000L, Math.min(2_400_000_000L, length * 19_000_000L));
        if (elapsed >= duration || UiPreferences.configured().reducedMotion()) {
            typing = null;
            return Integer.MAX_VALUE;
        }
        return 1 + (int) (length * elapsed / duration);
    }
}

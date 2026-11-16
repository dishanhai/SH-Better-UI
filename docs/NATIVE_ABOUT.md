# Native SHBUI About page

Entry: control center → Feedback → About SHBUI. Returns to the same control-center instance, preserving unsaved draft/navigation/scroll. It does not save configuration.

The page reads mod version from loader metadata and Minecraft version from SharedConstants, never from the integration reference. Loader ID comes from the existing platform bridge. HUD availability is the live native module state; provider/state/capabilities are the current music snapshot when the page opens. Missing sessions do not imply playback/artwork support. Links use SHBUI's existing confirmation/focus-return flow.

Text wraps and scrolls with the wheel, Page Up/Down, Home/End, or Tab-focused scroll buttons. Links and Back remain outside the clipped text viewport. Screen narration includes the complete textual information. Theme text/background and native UiButton styling are reused. No animation, external fonts, or MP runtime is needed.

Source comparison: MuoniumPlayer-multiversion revision 9b4cc7e7, muonium-common/src/main/java/com/muoniumplayer/core/screens/hud/MConfigHudScreen.java. This reference contains a HUD configuration/navigation shell, but no About page or About text was found. This page is an original SHBUI implementation of the requested information architecture, not a port of an upstream About page. SHBUI remains MIT; existing adapted components retain their Apache-2.0 upstream notices and CHANGES in META-INF. No MP branding/logo or Apple font asset copied.

Tests cover scroll clamping and identity. Runtime verification should cover both loaders, narrow GUI scale, long translated text, keyboard-only navigation, confirmation cancellation and return focus. Media/task/Automix backends are not introduced by this page.

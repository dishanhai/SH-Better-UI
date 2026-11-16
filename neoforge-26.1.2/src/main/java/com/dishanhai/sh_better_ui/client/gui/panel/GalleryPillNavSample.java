// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

final class GalleryPillNavSample extends GallerySampleScreen {
    private final PillNavDemo demo = new PillNavDemo();
    GalleryPillNavSample(DeveloperGalleryScreen parent, String id) { super(parent, id); }
    @Override protected void initSample() { demo.mount(font, body, preferences, this::addRenderableWidget); }
    @Override protected void cancelInteraction() { demo.cancelInteraction(); }
}

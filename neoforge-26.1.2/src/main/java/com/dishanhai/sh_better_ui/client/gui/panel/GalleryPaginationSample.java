// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

final class GalleryPaginationSample extends GallerySampleScreen {
    private final PaginationDemo demo = new PaginationDemo();
    GalleryPaginationSample(DeveloperGalleryScreen parent, String id) { super(parent, id); }
    @Override protected void initSample() { demo.mount(font, body, preferences, this::addRenderableWidget, () -> true); }
    @Override protected void cancelInteraction() { demo.cancelInteraction(); }
}

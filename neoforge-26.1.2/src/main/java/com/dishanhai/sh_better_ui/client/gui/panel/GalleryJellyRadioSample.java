// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

final class GalleryJellyRadioSample extends GallerySampleScreen {
    private String selected = JellyRadioDemo.DEFAULT_VALUE;

    GalleryJellyRadioSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        var control = JellyRadioDemo.layout(body, preferences, selected, (value, index) -> selected = value);
        if (control != null) initialFocus = addRenderableWidget(control);
    }
}

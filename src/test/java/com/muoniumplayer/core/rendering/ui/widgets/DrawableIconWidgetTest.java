package com.muoniumplayer.core.rendering.ui.widgets;

import com.muoniumplayer.core.utils.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DrawableIconWidgetTest {
    @AfterEach
    void resetLoader() {
        ThemedTextureIconWidget.setDefaultTextureLoader(location -> {});
    }

    @Test
    void painterReceivesCurrentWidgetBoundsAndColor() {
        List<Object> arguments = new ArrayList<>();
        DrawableIconWidget<?> widget = new DrawableIconWidget<>(
                (x, y, width, height, color) -> {
                    arguments.add(x);
                    arguments.add(y);
                    arguments.add(width);
                    arguments.add(height);
                    arguments.add(color);
                }, 12, 12);
        widget.setBounds(3, 5, 14, 16);
        widget.setColor(new Color(20, 40, 60));
        widget.onRender(0, 0);
        assertEquals(List.of(3.0, 5.0, 14.0, 16.0, widget.getHexColor()), arguments);
    }

    @Test
    void existingConstructorsUseTheHostLoaderAndAllowPerWidgetOverride() {
        List<Location> hostRequests = new ArrayList<>();
        List<Location> localRequests = new ArrayList<>();
        Location location = Location.of("/muonium/test-icon.png");
        ThemedTextureIconWidget widget = new ThemedTextureIconWidget(location, null, 0, 0, 20, 20);
        ThemedTextureIconWidget.setDefaultTextureLoader(hostRequests::add);
        widget.requestTexture(location);
        assertSame(location, hostRequests.get(0));

        widget.setTextureLoader(localRequests::add);
        widget.requestTexture(location);
        assertEquals(1, hostRequests.size());
        assertSame(location, localRequests.get(0));
    }
}

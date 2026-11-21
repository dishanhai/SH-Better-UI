package com.muoniumplayer.core.rendering.ui.widgets;

import com.muoniumplayer.core.rendering.ui.AbstractWidget;

import java.util.Objects;

/** A widget that delegates icon painting while retaining the shared widget interaction model. */
public class DrawableIconWidget<SELF extends DrawableIconWidget<SELF>> extends AbstractWidget<SELF> {
    @FunctionalInterface
    public interface Painter {
        void draw(double x, double y, double width, double height, int color);
    }

    private final Painter painter;

    public DrawableIconWidget(Painter painter, double width, double height) {
        this.painter = Objects.requireNonNull(painter);
        setBounds(width, height);
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        painter.draw(getX(), getY(), getWidth(), getHeight(), getHexColor());
    }
}

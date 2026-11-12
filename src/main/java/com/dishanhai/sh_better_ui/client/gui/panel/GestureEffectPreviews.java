// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiCollisionLayout;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionShape;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionWorld;
import com.dishanhai.sh_better_ui.client.gui.collision.CompressionModel;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPullRefresh;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

final class GestureEffectPreviews {
    private static final String LABEL = "sh_better_ui.panel.developer.gallery.";
    private static final int BUTTON_HEIGHT = 20;
    private static final int PRIMARY = 0;

    private GestureEffectPreviews() {}

    static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec("sh_better_ui:elastic_collision", PreviewSpec.Category.MATERIALS,
                340, LABEL + "sample.elastic_collision", 106, 0, "", ElasticCollision::new));
        registry.register(new PreviewSpec("sh_better_ui:pull_refresh", PreviewSpec.Category.MATERIALS,
                341, LABEL + "sample.pull_refresh", 106, 0, "", PullRefresh::new));
    }

    private static Bounds content(Bounds viewport) {
        return new Bounds(viewport.x() + 6, viewport.y() + 6, Math.max(0, viewport.width() - 12),
                Math.max(0, viewport.height() - BUTTON_HEIGHT - 18));
    }

    private static Rect2i rect(Bounds bounds) {
        return new Rect2i(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    private static Bounds footer(Bounds area) {
        return new Bounds(area.x(), area.bottom() + 6, area.width(), BUTTON_HEIGHT);
    }

    private static final class ElasticCollision implements PreviewScene {
        private PreviewContext host;
        private Bounds lane;
        private UiCollisionLayout layout;
        private CollisionBody moving;
        private CollisionBody elastic;
        private UiPointerCapture.Registration press;
        private boolean dragging;
        private float restX;
        private float collisionX;
        private float origin;
        private double grabOffset;

        @Override
        public void layout(PreviewContext host) {
            suspend();
            this.host = host;
            lane = content(host.viewport());
            if (lane.width() < 64 || lane.height() < 24) return;
            layout = new UiCollisionLayout(lane);
            var world = layout.world().axis(CollisionWorld.Axis.HORIZONTAL);
            int width = lane.width() / 3;
            int height = Math.min(30, lane.height() - 8);
            int y = lane.y() + (lane.height() - height) / 2;
            restX = lane.x() + 4;
            collisionX = lane.x() + lane.width() / 2f - width / 4f;
            moving = world.add(new CollisionShape(restX, y, width, height)).priority(10).direct(true);
            elastic = world.add(new CollisionShape(lane.right() - width - 4, y, width, height))
                    .strategy(CollisionBody.Strategy.ELASTIC)
                    .compression(new CompressionModel(width / 2f, height, width, height, 1));
            layout.update(lane, PageTransition.nowMs(), host.preferences().reducedMotion());
            var button = footer(lane);
            var toggle = host.add(new UiButton(button.x(), button.y(), button.width(), button.height(),
                    Component.translatable(LABEL + "collision_toggle"),
                    ignored -> {
                        if (!dragging) move(moving.desired().x() == restX ? collisionX : restX);
                    }, host.preferences(), false));
            toggle.setPreview(Component.translatable(LABEL + "collision_hint"));
            press = ShBetterUiApi.interactions().registerPointerPress(host.screen(), this::begin);
        }

        private boolean begin(double x, double y, int button) {
            if (button != PRIMARY || !host.interactive() || !moving.visual().contains(x, y)) return false;
            origin = moving.desired().x();
            grabOffset = x - moving.visualX();
            dragging = ShBetterUiApi.interactions().capturePointer(host.screen(), button, new UiPointerCapture.Handler() {
                @Override public void drag(double x, double y) { move(x - grabOffset); }
                @Override public void release(double x, double y) {
                    dragging = false;
                    move(lane.contains(x, y) ? x - grabOffset : origin);
                }
                @Override public void cancel() {
                    dragging = false;
                    move(origin);
                }
            });
            return dragging;
        }

        private void move(double x) {
            if (Double.isFinite(x)) {
                float position = (float) Math.max(lane.x(), Math.min(lane.right() - moving.desired().width(), x));
                moving.moveTo(position, moving.desired().y());
            }
        }

        @Override
        public void render(UiFrame frame) {
            if (layout == null) return;
            layout.update(lane, frame.nowMs(), frame.preferences().reducedMotion());
            var graphics = frame.graphics();
            DarkModeTheme.background(graphics, lane.x(), lane.y(), lane.width(), lane.height());
            var movable = moving.visual();
            var target = elastic.visual();
            int foreground = DarkModeTheme.textColor();
            RoundedCorners.draw(graphics, Math.round(target.x()), Math.round(target.y()),
                    Math.round(target.width()), Math.round(target.height()), 4, 1, foreground, DarkModeTheme.surfaceColor());
            int left = Math.round(movable.x());
            int top = Math.round(movable.y());
            int width = Math.round(movable.width());
            int height = Math.round(movable.height());
            RoundedCorners.draw(graphics, left, top, width, height, 4, 1,
                    dragging ? foreground : DarkModeTheme.accentColor(), DarkModeTheme.surfaceColor());
            for (int i = -1; i <= 1; i++) {
                int x = left + width / 2 + i * 4;
                graphics.fill(x, top + height / 2 - 2, x + 1, top + height / 2 + 3, foreground);
            }
        }

        @Override
        public void suspend() {
            if (press != null) {
                press.close();
                press = null;
            }
            if (dragging) ShBetterUiApi.interactions().cancelPointer(host.screen());
            dragging = false;
            if (layout != null) {
                layout.close();
                layout = null;
            }
        }
    }

    private static final class PullRefresh implements PreviewScene {
        private final UiPullRefresh refresh = ShBetterUiApi.interactions().pullRefresh(
                new UiPullRefresh.Config(6, 24, 20, ButtonFeedback.PENDING_TIMEOUT_MS), this::request);
        private PreviewContext host;
        private Bounds area;
        private Bounds handle;
        private UiPointerCapture.Registration press;
        private Integer pending;
        private long completesAt;

        @Override
        public void layout(PreviewContext host) {
            clearGesture();
            this.host = host;
            area = content(host.viewport());
            if (area.width() < 52 || area.height() < 28) return;
            handle = new Bounds(area.x(), area.y(), area.width(), Math.min(20, area.height()));
            host.add(refresh.createButton(rect(footer(area)), host.preferences()));
            press = ShBetterUiApi.interactions().registerPointerPress(host.screen(), this::begin);
        }

        private boolean begin(double x, double y, int button) {
            if (!host.interactive() || !handle.contains(x, y)
                    || !refresh.begin(button, x, y, true, PageTransition.nowMs())) return false;
            boolean captured = ShBetterUiApi.interactions().capturePointer(host.screen(), button, new UiPointerCapture.Handler() {
                @Override public void drag(double x, double y) {
                    refresh.drag(button, x, y, area.contains(x, y), PageTransition.nowMs());
                }
                @Override public void release(double x, double y) {
                    refresh.release(button, x, y, area.contains(x, y), PageTransition.nowMs());
                }
                @Override public void cancel() { refresh.cancelGesture(PageTransition.nowMs()); }
            });
            if (!captured) refresh.cancelGesture(PageTransition.nowMs());
            return captured;
        }

        private void request(int id) {
            pending = id;
            completesAt = PageTransition.nowMs() + 600L;
        }

        @Override
        public void render(UiFrame frame) {
            if (handle == null) return;
            if (pending != null && frame.nowMs() >= completesAt) {
                refresh.complete(pending, ButtonFeedback.Tone.SUCCESS, frame.nowMs());
                pending = null;
            }
            DarkModeTheme.background(frame.graphics(), area.x(), area.y(), area.width(), area.height());
            int reveal = Math.round(refresh.sample(frame.nowMs(), frame.preferences()).reveal());
            int height = Math.min(area.height(), handle.height() + reveal);
            var indicator = new Rect2i(handle.x(), handle.y(), handle.width(), height);
            refresh.render(frame.graphics(), host.font(), indicator, rect(area), frame.nowMs(), frame.preferences());
            for (int y = area.y() + height + 5; y + 4 <= area.bottom() && y < area.y() + height + 29; y += 8) {
                frame.graphics().fill(area.x() + 6, y, area.right() - 6, y + 4, DarkModeTheme.surfaceColor());
            }
        }

        private void clearGesture() {
            if (press != null) {
                press.close();
                press = null;
            }
            if (refresh.isDragging()) ShBetterUiApi.interactions().cancelPointer(host.screen());
            handle = null;
        }

        @Override
        public void suspend() {
            clearGesture();
            pending = null;
            refresh.reset(PageTransition.nowMs());
        }
    }
}

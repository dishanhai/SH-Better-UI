// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogoProfile;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.AntigravityProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.CodeRainProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.GalleryFieldDotMatrix;
import com.dishanhai.sh_better_ui.client.gui.scaled.GalleryFieldOptics;
import com.dishanhai.sh_better_ui.client.gui.scaled.GalleryFieldParticles;
import com.dishanhai.sh_better_ui.client.gui.scaled.LaserFlowProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.LightRaysProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWavesProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerTracker;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

final class GalleryFieldSamples extends GallerySampleScreen {
    private static final String GALLERY = "sh_better_ui.panel.developer.gallery.";
    private static final int ROW = 20;
    private static final int GAP = 4;
    private static final String[] DOT_PALETTES = {"rainbow", "reverse", "aurora", "solid"};
    private static final String[] DOT_FLOWS = {"right", "down", "diagonal", "outward"};
    private static final String[] LOGOS = {"bolt", "star", "creeper"};
    private static final int[] WAVE_COLORS = {0xFF90BEFF, 0xFF65D3FC, 0xFF85DDCA, 0xFFD4A4FF, 0xFF74F0AA, 0xFFF7AFDA};
    private static final UiPointerTracker.Frame OUTSIDE = new UiPointerTracker.Frame(
            0, 0, false, false, false, false, false, Long.MAX_VALUE);

    private final GalleryFieldDotMatrix dots = new GalleryFieldDotMatrix();
    private final GalleryFieldOptics optics = new GalleryFieldOptics();
    private final GalleryFieldParticles particles = new GalleryFieldParticles();

    private Bounds canvas;
    private UiButton pauseControl;
    private UiButton flowControl;
    private double time;
    private long lastFrame;
    private double pulseTime = -1;
    private float pulseX;
    private float pulseY;
    private float charge;
    private boolean paused;
    private boolean holding;
    private boolean released;
    private boolean pointerInside;
    private int lastPointerX;
    private int lastPointerY;
    private long lastPointerMove;

    private int dotPalette;
    private int dotFlow;
    private int fadeChance = 35;
    private int wavePreset;
    private int rayOrigin = LightRaysProfile.Origin.TOP_CENTER.ordinal();
    private int rayIntensity = 120;
    private boolean lightBoard;
    private int logoShape;
    private int logoPreset;
    private int particleShape;
    private int particleColor;
    private int particleCount = 600;
    private boolean ascii;
    private int rainSpeed = 8;
    private int beamWidth = 15;
    private int beamPosition = 60;

    private DotMatrixProfile dotProfile;
    private PatternWavesProfile waveProfile;
    private LightRaysProfile rayProfile;
    private ElectricLogoProfile logoProfile;
    private AntigravityProfile particleProfile;
    private CodeRainProfile rainProfile;
    private LaserFlowProfile laserProfile;

    private GalleryFieldSamples(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
        updateProfile();
    }

    static GallerySampleScreen create(DeveloperGalleryScreen parent, String id) {
        return new GalleryFieldSamples(parent, id);
    }

    @Override
    protected void initSample() {
        boolean extraRow = id.equals("antigravity") && body.height() >= 102;
        int controlsHeight = extraRow ? 2 * (ROW + GAP) : ROW + GAP;
        canvas = new Bounds(body.x(), body.y(), body.width(), body.height() - controlsHeight);
        pauseControl = null;
        flowControl = null;
        initialFocus = addRenderableWidget(new Canvas());
        switch (id) {
            case "dot_matrix" -> {
                cycle(0, 4, 0, text("gradient", text(DOT_PALETTES[dotPalette])), () -> {
                    dotPalette = (dotPalette + 1) % DOT_PALETTES.length;
                    return text("gradient", text(DOT_PALETTES[dotPalette]));
                });
                flowControl = cycle(1, 4, 0, text("flow", text(DOT_FLOWS[dotFlow])), () -> {
                    dotFlow = (dotFlow + 1) % DOT_FLOWS.length;
                    return text("flow", text(DOT_FLOWS[dotFlow]));
                });
                flowControl.active = dotPalette != 3;
                slider(2, 4, 0, 0, 100, 5, fadeChance, value -> text("fade_chance", value),
                        value -> fadeChance = value);
                pauseButton(3, 4, 0);
            }
            case "pattern_waves" -> {
                cycle(0, 3, 0, text("preset", option(PatternWavesProfile.Preset.values()[wavePreset])), () -> {
                    wavePreset = (wavePreset + 1) % PatternWavesProfile.Preset.values().length;
                    return text("preset", option(PatternWavesProfile.Preset.values()[wavePreset]));
                });
                pauseButton(1, 3, 0);
                replayButton(2, 3, 0);
            }
            case "light_rays" -> {
                cycle(0, 4, 0, text("origin", option(LightRaysProfile.Origin.values()[rayOrigin])), () -> {
                    rayOrigin = (rayOrigin + 1) % LightRaysProfile.Origin.values().length;
                    return text("origin", option(LightRaysProfile.Origin.values()[rayOrigin]));
                });
                slider(1, 4, 0, 20, 250, 5, rayIntensity, value -> text("percent", text("intensity"), value),
                        value -> rayIntensity = value);
                cycle(2, 4, 0, text(lightBoard ? "light_board" : "dark_board"), () -> {
                    lightBoard = !lightBoard;
                    return text(lightBoard ? "light_board" : "dark_board");
                });
                replayButton(3, 4, 0);
            }
            case "electric_logo" -> {
                cycle(0, 4, 0, text("change", text(LOGOS[logoShape])), () -> {
                    logoShape = (logoShape + 1) % LOGOS.length;
                    return text("change", text(LOGOS[logoShape]));
                });
                cycle(1, 4, 0, logoPresetLabel(), () -> {
                    logoPreset = (logoPreset + 1) % ElectricLogoProfile.Preset.values().length;
                    return logoPresetLabel();
                });
                pauseButton(2, 4, 0);
                replayButton(3, 4, 0);
            }
            case "antigravity" -> {
                int count = extraRow ? 3 : 4;
                cycle(0, count, 0, text("shape", option(AntigravityProfile.Shape.values()[particleShape])), () -> {
                    particleShape = (particleShape + 1) % AntigravityProfile.Shape.values().length;
                    return text("shape", option(AntigravityProfile.Shape.values()[particleShape]));
                });
                cycle(1, count, 0, text("color_mode", option(AntigravityProfile.ColorMode.values()[particleColor])), () -> {
                    particleColor = (particleColor + 1) % AntigravityProfile.ColorMode.values().length;
                    return text("color_mode", option(AntigravityProfile.ColorMode.values()[particleColor]));
                });
                if (extraRow) {
                    slider(2, 3, 0, 200, 1200, 100, particleCount, value -> field("density", value),
                            value -> particleCount = value);
                    pauseButton(0, 2, 1);
                    replayButton(1, 2, 1);
                } else {
                    pauseButton(2, 4, 0);
                    replayButton(3, 4, 0);
                }
            }
            case "code_rain" -> {
                cycle(0, 4, 0, text(ascii ? "ascii" : "rain"), () -> {
                    ascii = !ascii;
                    return text(ascii ? "ascii" : "rain");
                });
                slider(1, 4, 0, 1, 20, 1, rainSpeed, value -> field("speed", value), value -> rainSpeed = value);
                pauseButton(2, 4, 0);
                replayButton(3, 4, 0);
            }
            case "laser_flow" -> {
                slider(0, 4, 0, 5, 50, 5, beamWidth, value -> field("beam_width", value / 10f),
                        value -> beamWidth = value);
                slider(1, 4, 0, 10, 90, 5, beamPosition, value -> field("beam_position", value),
                        value -> beamPosition = value);
                pauseButton(2, 4, 0);
                replayButton(3, 4, 0);
            }
            default -> throw new IllegalArgumentException(id);
        }
    }

    private void updateProfile() {
        switch (id) {
            case "dot_matrix" -> {
                DotMatrixProfile.Gradient palette = switch (dotPalette) {
                    case 1 -> DotMatrixProfile.Gradient.REVERSE_RAINBOW;
                    case 2 -> DotMatrixProfile.Gradient.AURORA;
                    case 3 -> DotMatrixProfile.Gradient.NONE;
                    default -> DotMatrixProfile.Gradient.RAINBOW;
                };
                DotMatrixProfile.Flow flow = switch (dotFlow) {
                    case 1 -> DotMatrixProfile.Flow.DOWN;
                    case 2 -> DotMatrixProfile.Flow.DIAGONAL;
                    case 3 -> DotMatrixProfile.Flow.OUTWARD;
                    default -> DotMatrixProfile.Flow.RIGHT;
                };
                dotProfile = DotMatrixProfile.VIOLET.withGrid(new DotMatrixProfile.Grid(8, 3, .72f, 27L))
                        .withGradient(new DotMatrixProfile.Gradient(palette.stops(), palette.periodMs(), flow))
                        .withFade(new DotMatrixProfile.Fade(fadeChance / 100f, 2400))
                        .withPointerWave(DotMatrixProfile.PointerWave.DEFAULT)
                        .withPointerLens(DotMatrixProfile.PointerLens.DEFAULT);
                dots.setProfile(dotProfile);
                if (flowControl != null) flowControl.active = dotPalette != 3;
            }
            case "pattern_waves" -> waveProfile = PatternWavesProfile.builder(PatternWavesProfile.Preset.values()[wavePreset])
                    .color(WAVE_COLORS[wavePreset]).backgroundColor(0xFF101521).build();
            case "light_rays" -> rayProfile = LightRaysProfile.builder()
                    .raysOrigin(LightRaysProfile.Origin.values()[rayOrigin]).intensity(rayIntensity / 100f)
                    .mouseInfluence(.72f).lightMode(lightBoard)
                    .raysColor(lightBoard ? 0xFF2C4EAB : 0xFFA8CDFF).build();
            case "electric_logo" -> {
                var preset = ElectricLogoProfile.Preset.values()[logoPreset].profile();
                logoProfile = preset.withLines(new ElectricLogoProfile.Lines(.67f, preset.lines().thickness(),
                        preset.lines().strands()));
            }
            case "antigravity" -> particleProfile = AntigravityProfile.DEFAULT.toBuilder().count(particleCount)
                    .particleShape(AntigravityProfile.Shape.values()[particleShape])
                    .colorMode(AntigravityProfile.ColorMode.values()[particleColor]).build();
            case "code_rain" -> rainProfile = CodeRainProfile.builder().mode(ascii ? CodeRainProfile.Mode.ASCII
                    : CodeRainProfile.Mode.RAIN).speed(rainSpeed).seed(17).cellSize(12).build();
            case "laser_flow" -> laserProfile = LaserFlowProfile.DEFAULT.toBuilder().beamWidth(beamWidth / 10f)
                    .beamPosition(beamPosition / 100f).surfaceLevel(.77f).color(0xFF607FFF).build();
        }
    }

    private Bounds control(int index, int count, int row) {
        int usable = body.width() - (count - 1) * GAP;
        int left = usable * index / count;
        int right = usable * (index + 1) / count;
        return new Bounds(body.x() + left + index * GAP, canvas.bottom() + GAP + row * (ROW + GAP),
                right - left, ROW);
    }

    private UiButton cycle(int index, int count, int row, Component label, Supplier<Component> next) {
        var button = addButton(control(index, count, row), label, pressed -> {
            pressed.setMessage(next.get());
            updateProfile();
        }, false);
        button.setSampleBackground(false);
        return button;
    }

    private void slider(int index, int count, int row, int min, int max, int step, int value,
                        IntFunction<Component> label, IntConsumer update) {
        IntConsumer change = selected -> {
            update.accept(selected);
            updateProfile();
        };
        var slider = new UiSlider(control(index, count, row), min, max, step, value, preferences, label, change);
        slider.setOnCommit(ignored -> {}, change);
        addRenderableWidget(slider);
    }

    private void pauseButton(int index, int count, int row) {
        var button = addButton(control(index, count, row), pauseLabel(), pressed -> {
            paused = !paused;
            cancelInteraction();
            pressed.setMessage(pauseLabel());
        }, false);
        button.setSampleBackground(false);
        button.active = !motionDisabled();
        pauseControl = button;
    }

    private void replayButton(int index, int count, int row) {
        var button = addButton(control(index, count, row), field("replay"), ignored -> {
            cancelInteraction();
            paused = false;
            if (pauseControl != null) pauseControl.setMessage(pauseLabel());
            time = 0;
            optics.restartRays();
            particles.restart();
        }, false);
        button.setSampleBackground(false);
        button.active = !motionDisabled();
    }

    private Component pauseLabel() {
        return field(motionDisabled() ? "still" : paused ? "resume" : "pause");
    }

    private Component logoPresetLabel() {
        return field("preset", field(ElectricLogoProfile.Preset.values()[logoPreset].name().toLowerCase(Locale.ROOT)));
    }

    private Component option(Enum<?> value) {
        return text(value.name().toLowerCase(Locale.ROOT));
    }

    private static Component field(String key, Object... args) {
        return Component.translatable(GALLERY + "field." + key, args);
    }

    private Component usage() {
        if (motionDisabled()) return field("still_hint");
        return switch (id) {
            case "dot_matrix" -> text("pulse");
            case "code_rain", "laser_flow" -> field("pulse_hint");
            default -> text("hint");
        };
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (holding && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && !canvas.contains(event.x(), event.y())) {
            cancelInteraction();
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (holding && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            holding = false;
            released = minecraft.isWindowActive() && canvas.contains(event.x(), event.y()) && id.equals("antigravity");
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && holding) {
            cancelInteraction();
            setDragging(false);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void cancelInteraction() {
        holding = released = pointerInside = false;
        pulseTime = -1;
        charge = 0;
        lastFrame = 0;
        dots.clearPointer();
        optics.clearPointer();
        particles.cancel();
    }

    private boolean moving() {
        return !paused && !motionDisabled() && minecraft.isWindowActive();
    }

    private boolean motionDisabled() {
        return preferences.reducedMotion() || preferences.highContrast()
                && (id.equals("dot_matrix") || id.equals("antigravity"));
    }

    private void trigger(float x, float y) {
        pulseX = x;
        pulseY = y;
        pulseTime = time;
        if (id.equals("light_rays")) optics.pulseRays();
    }

    private UiPointerTracker.Frame pointer(int mouseX, int mouseY) {
        if (!moving() || !canvas.contains(mouseX, mouseY)) {
            if (holding) cancelInteraction();
            pointerInside = false;
            released = false;
            return OUTSIDE;
        }
        long now = Util.getMillis();
        if (!pointerInside || lastPointerX != mouseX || lastPointerY != mouseY) lastPointerMove = now;
        lastPointerX = mouseX;
        lastPointerY = mouseY;
        pointerInside = true;
        var frame = new UiPointerTracker.Frame(mouseX - canvas.x(), mouseY - canvas.y(), true, holding,
                false, released, false, Math.max(0, now - lastPointerMove));
        released = false;
        return frame;
    }

    private void drawCanvas(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean focused) {
        long now = System.nanoTime();
        boolean reduced = motionDisabled();
        boolean contrast = preferences.highContrast();
        boolean active = moving();
        float dt = active && lastFrame != 0 ? (float) Math.clamp((now - lastFrame) * 1e-9, 0, .05) : 0;
        lastFrame = now;
        time += dt;
        double seconds = reduced ? 0 : time;
        var pointer = pointer(mouseX, mouseY);
        double age = pulseTime < 0 ? -1 : seconds - pulseTime;
        int background = contrast ? 0xFF080808 : switch (id) {
            case "dot_matrix" -> dotProfile.palette().backgroundArgb();
            case "pattern_waves" -> waveProfile.backgroundColor();
            case "light_rays" -> lightBoard ? 0xFFDCE4F2 : 0xFF0B1220;
            case "electric_logo" -> 0xFF130C20;
            case "antigravity" -> 0xFF100A1B;
            case "code_rain" -> 0xFF071612;
            default -> 0xFF0C1026;
        };
        graphics.fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), background);
        graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
        try {
            switch (id) {
                case "dot_matrix" -> dots.draw(graphics, canvas, contrast ? OUTSIDE : pointer,
                        (long) (seconds * 1000), reduced, contrast, pulseX, pulseY, age);
                case "pattern_waves" -> GalleryFieldOptics.drawWaves(graphics, font, canvas, waveProfile, seconds, pointer,
                        pulseX, pulseY, age, reduced, contrast);
                case "light_rays" -> optics.drawRays(graphics, canvas, rayProfile, pointer, dt, reduced, contrast);
                case "electric_logo" -> {
                    double distance = Math.hypot(pointer.x() - canvas.width() * .5, pointer.y() - canvas.height() * .5);
                    float target = pointer.inside() ? (float) Math.max(0, 1 - distance / logoProfile.interaction().cursorRadius()) : 0;
                    if (holding) target = 1;
                    charge += (target - charge) * (float) -Math.expm1(-dt / .16);
                    GalleryFieldOptics.drawLogo(graphics, canvas, logoProfile, logoShape, seconds, charge, age, reduced, contrast);
                }
                case "antigravity" -> particles.draw(graphics, canvas, particleProfile, contrast ? OUTSIDE : pointer,
                        dt, reduced, contrast);
                case "code_rain" -> GalleryFieldParticles.drawRain(graphics, font, canvas, rainProfile, seconds, pointer,
                        pulseX, pulseY, age, contrast);
                case "laser_flow" -> {
                    int y = canvas.y() + (int) (canvas.height() * laserProfile.surfaceLevel());
                    graphics.fill(canvas.x(), y, canvas.right(), canvas.bottom(), contrast ? 0xFF171717 : 0xFF141C36);
                    GalleryFieldOptics.drawLaser(graphics, canvas, laserProfile, seconds, pointer, age, reduced, contrast);
                }
            }
        } finally {
            graphics.disableScissor();
        }
        int edge = focused || holding ? theme.text() : theme.border();
        int x = canvas.x(), y = canvas.y(), right = canvas.right(), bottom = canvas.bottom();
        graphics.fill(x, y, right, y + 1, edge);
        graphics.fill(x, bottom - 1, right, bottom, edge);
        graphics.fill(x, y, x + 1, bottom, edge);
        graphics.fill(right - 1, y, right, bottom, edge);
    }

    private final class Canvas extends AbstractWidget {
        private final Component hint = usage();

        Canvas() {
            super(canvas.x(), canvas.y(), canvas.width(), canvas.height(), GalleryFieldSamples.this.getTitle());
            setTooltip(Tooltip.create(hint));
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!canvas.contains(event.x(), event.y()) || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
            if (moving()) {
                holding = true;
                if (!id.equals("antigravity")) trigger((float) (event.x() - canvas.x()), (float) (event.y() - canvas.y()));
            }
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!isFocused() || !event.isSelection() || !moving()) return false;
            if (id.equals("antigravity")) particles.burst(particleProfile, canvas);
            else trigger(canvas.width() * .5f, canvas.height() * .5f);
            return true;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused && holding) cancelInteraction();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            output.add(NarratedElementType.USAGE, hint);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawCanvas(graphics, mouseX, mouseY, isFocused());
        }
    }
}

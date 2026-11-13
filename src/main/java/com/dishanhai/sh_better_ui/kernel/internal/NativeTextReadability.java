// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageTextReadability;
import com.dishanhai.sh_better_ui.kernel.api.text.TextId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Request;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Run;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Style;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.function.Supplier;

/** Native adapter for positioned Minecraft font lines and client-local page overrides. */
public final class NativeTextReadability {
    private NativeTextReadability() {}

    public record Mount(ModuleContext module, ScopeHandle scope) {
        public TextReadabilityService service() { return module.engine().require(UIApis.TEXT_READABILITY); }
    }

    public record Options(TextReadabilityPolicy policy, TextReadabilityService.SamplingHint sampling) {
        public static final Options INHERIT = new Options(TextReadabilityPolicy.inherit(), null);

        public static Options fromPage(UiPageTextReadability.Options options) {
            var hint = options.sampling() == null ? null
                    : TextReadabilityService.SamplingHint.valueOf(options.sampling().name());
            return new Options(options.policy(), hint);
        }

        public UiPageTextReadability.Options forPage() {
            var hint = sampling == null ? null : UiPageTextReadability.Sampling.valueOf(sampling.name());
            return new UiPageTextReadability.Options(policy, hint);
        }

        public Options overlay(Options overrides) {
            return new Options(policy.overlay(overrides.policy),
                    overrides.sampling == null ? sampling : overrides.sampling);
        }
    }

    public interface EditBoxBinding {
        void shbui$bindReadability(Label label);
    }

    public record Label(Mount mount, UiId id, Options options, Supplier<Bounds> clip) {
        public Label inline() {
            return new Label(mount, id, new Options(options.policy().overlay(
                    new TextReadabilityPolicy(null, false, null, null, null)), options.sampling()), clip);
        }

        public boolean drawClipped(GuiGraphics graphics, Font font, FormattedCharSequence glyphs, int x, int y,
                                   int color, boolean shadow, int line, boolean hovered, boolean focused, Rect clip) {
            return draw(graphics, font, glyphs, x, y, color, shadow, line, hovered, focused, null, clip);
        }

        public boolean draw(GuiGraphics graphics, Font font, FormattedCharSequence glyphs, int x, int y, int color,
                            boolean shadow, int lineNumber, boolean hovered, boolean focused) {
            return draw(graphics, font, glyphs, x, y, color, shadow, lineNumber, hovered, focused, null, null);
        }

        boolean draw(GuiGraphics graphics, Font font, FormattedCharSequence glyphs, int x, int y, int color,
                     boolean shadow, int lineNumber, boolean hovered, boolean focused, UiId dataItem, Rect rowClip) {
            if (!mount.scope().isOpen()) return false;
            var service = mount.service();
            Boolean adaptive = options.policy().adaptiveColor();
            Boolean protectedForeground = options.policy().foregroundProtection();
            if (adaptive == null || protectedForeground == null) {
                var inherited = service.policy(mount.module(), mount.scope());
                if (adaptive == null) adaptive = inherited.adaptiveColor();
                if (protectedForeground == null) protectedForeground = inherited.foregroundProtection();
            }
            if (!adaptive && !protectedForeground) return false;
            var sampling = options.sampling();
            if (sampling == null && focused) sampling = TextReadabilityService.SamplingHint.AUTO_FOCUSED;
            if (sampling == null && hovered) sampling = TextReadabilityService.SamplingHint.AUTO_HOVERED;
            var text = new TextId(id, dataItem, new UiId(id.namespace(), "line/" + lineNumber));
            var effectiveClip = rect(clip.get());
            if (rowClip != null) effectiveClip = effectiveClip.intersect(rowClip);
            var request = line(mount.scope(), text, font, glyphs, x, y,
                    new Style(color, shadow, TextReadabilityPolicy.Usage.BODY), transform(graphics), effectiveClip,
                    1, null, 0, new Options(options.policy(), sampling));
            if (request.run().bounds().empty()) return false;
            return switch (service.submit(mount.module(), request)) {
                case SUBMITTED, DRAWN, CLIPPED, HIDDEN -> true;
                default -> false;
            };
        }

        void retire(UiId item) {
            if (mount.scope().isOpen()) mount.service().retireDataItem(mount.module(), mount.scope(), item);
        }
    }

    public static Rect rect(Bounds bounds) {
        return new Rect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    public static Transform transform(GuiGraphics graphics) {
        var pose = graphics.pose().last().pose();
        return new Transform(pose.m00(), pose.m10(), pose.m01(), pose.m11(), pose.m30(), pose.m31());
    }

    public static Request line(ScopeHandle scope, TextId id, Font font, FormattedCharSequence line,
                               double x, double y, Style style, Transform pose, Rect clip, float opacity,
                               Integer background, int z, Options options) {
        var bounds = new Rect(x, y, font.width(line), font.lineHeight);
        var request = new Request(scope, id, new Run(line, x, y, bounds, font.lineHeight, font), style, pose, clip,
                opacity, background, z).withPolicy(options.policy());
        return options.sampling() == null ? request : request.withSampling(options.sampling());
    }
}

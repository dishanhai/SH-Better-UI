// Modified for SHBUI by dishanhai. See META-INF/third-party/muonium-player-2.6.0/SOURCE.
package com.muoniumplayer.core.rendering.ui.container;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.music.control.ScrollEndPull;
import com.muoniumplayer.compat.lwjgl2.Keyboard;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.rendering.Rect;
import com.muoniumplayer.core.rendering.ScissorClipManager;
import com.muoniumplayer.core.rendering.StencilClipManager;
import com.muoniumplayer.core.rendering.rendersystem.RenderSystem;
import com.muoniumplayer.core.rendering.ui.AbstractWidget;
import com.dishanhai.sh_better_ui.kernel.api.controls.WidgetTheme;
import com.dishanhai.sh_better_ui.kernel.api.controls.WidgetTouch;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import com.muoniumplayer.core.utils.cursor.CursorUtils;
import lombok.Getter;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 一个从上至下排列的可以滚动的容器
 */
public class ScrollPanel extends AbstractWidget<ScrollPanel> {

    /** A fixed sentinel stays outside the viewport even when the panel is resized next frame. */
    private static final double OUTSIDE_VIEWPORT_Y = -1_000_000.0;

    /** Null means unrestricted; the rendering host may scope raw touch polling to one modal tree. */
    private static final ThreadLocal<AbstractWidget<?>> TOUCH_INPUT_ROOT = new ThreadLocal<>();

    /**
     * Restrict raw touch drag polling to {@code root} and its descendants on the calling thread.
     * A null root explicitly permits all scroll panels. Always close the scope, preferably with
     * try-with-resources; nested scopes restore the previous restriction, including after errors.
     */
    public static TouchInputScope restrictTouchInputTo(AbstractWidget<?> root) {
        return new TouchInputScope(root);
    }

    /** Same-thread, lexically nested touch restriction; close is idempotent. */
    public static final class TouchInputScope implements AutoCloseable {
        private final AbstractWidget<?> previous;
        private boolean closed;

        private TouchInputScope(AbstractWidget<?> root) {
            previous = TOUCH_INPUT_ROOT.get();
            if (root == null) TOUCH_INPUT_ROOT.remove();
            else TOUCH_INPUT_ROOT.set(root);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) TOUCH_INPUT_ROOT.remove();
            else TOUCH_INPUT_ROOT.set(previous);
        }
    }

    /** Subclasses can deny raw touch polling during entrance, exit or another local input lock. */
    protected boolean acceptsTouchInput() {
        return true;
    }

    /** Package-private pure gate for tests; never consults Minecraft, native state or gestures. */
    final boolean canPollTouchInput() {
        if (!acceptsTouchInput()) return false;
        AbstractWidget<?> root = TOUCH_INPUT_ROOT.get();
        if (root == null) return true;
        for (AbstractWidget<?> widget = this; widget != null; widget = widget.getParent()) {
            if (widget == root) return true;
        }
        return false;
    }

    @Getter
    private double spacing = 0;
    @Getter
    private double scrollStrength = 12;
    public double actualScrollOffset = 0, targetScrollOffset = 0;

    /** 最近一次滚轮输入，用于在快速滚动时跳过新条目的二级进场矩阵。 */
    private long lastScrollInputNanos;

    // ──────────────────────── 滚动指示条 ────────────────────────
    // 播放器里所有列表以前都没有滚动条，240 张卡的内容长度完全不可见。这里补一条「只在需要时
    // 出现」的极细指示条：不占布局，画在裁剪之后。宽度与圆角
    // 走设计档 2.5L / 1.25L，颜色取主题次要文字色再乘透明度，深浅主题都成立。

    /** 指示条宽（布局单位）。 */
    private static final double INDICATOR_WIDTH = 2.5;
    /**
     * 指示条中心离视口右缘的距离（正值 = 落在视口右侧的留白里，负值 = 视口内侧）。
     *
     * <p>为什么默认画在<b>外侧</b>：主页网格、热搜列表的内容都是精确填满视口宽度的，
     * 指示条一旦画在视口内侧，就必然压在最右一列卡片/文字上（240 张卡的列表尤其明显）。
     * 而内容区两侧本来就有 12L 页面边距，这条 2.5L 的细条正好住在那条留白里，
     * 既贴着自己的内容，又不和任何控件抢像素。</p>
     */
    private static final double INDICATOR_INSET_RIGHT = -4.0;
    /** 显式要求画在视口内侧时的内缩量（浮层用，见 {@link #setIndicatorInsideViewport()}）。 */
    private static final double INDICATOR_INSET_INSIDE = 4.0;
    /** 内容极长时滑块的最小长度，保证还能看见。 */
    private static final double INDICATOR_MIN_LENGTH = 14.0;
    /** 滚动结束后指示条继续停留的时长，超过就开始淡出。 */
    private static final long INDICATOR_HOLD_NANOS = 900_000_000L;
    /** 淡入/淡出速度：按毫秒线性推进，帧率无关。 */
    private static final double INDICATOR_FADE_IN_PER_MS = 1.0 / 120.0;
    private static final double INDICATOR_FADE_OUT_PER_MS = 1.0 / 200.0;
    /** 同一帧内重复推进的帧时长上限，挡切后台回来这种断档。 */
    private static final double MAX_FRAME_MS = 64.0;

    private float indicatorAlpha;
    /** 是否把指示条画回视口内侧（浮层用；默认画在外侧页面留白里）。 */
    private boolean indicatorInsideViewport;
    /** 本帧滑块在屏幕上的位置/长度；由 onRender 算好，afterRenderChildren 里画。 */
    private double indicatorThumbTop;
    private double indicatorThumbLength;
    /** 本帧内容可滚动总长；绘制时不再二次扫描子项。 */
    private double indicatorMaximumOffset = -1.0;
    private VerticalScroll scrollbar;
    private double scrollbarViewport = -1;
    private double scrollbarMaximum = -1;
    private ScrollEndPull endPull;
    private BooleanSupplier endInput;
    private Supplier<EndStatus> endStatus;
    private Runnable loadNext;
    private double lastTouchY;

    public enum EndStatus { HIDDEN, MORE, LOADING, FAILED, END }

    /** Adds bottom-edge pagination to a vertical list. The owner supplies the request status. */
    public void setEndPull(BooleanSupplier inputAllowed, Supplier<EndStatus> status, Runnable loadNext) {
        this.endPull = new ScrollEndPull();
        this.endInput = inputAllowed;
        this.endStatus = status;
        this.loadNext = loadNext;
    }

    public boolean cancelEndPull() {
        if (this.endPull == null || !this.touchDragging) return false;
        this.touchDragging = false;
        this.endPull.reset();
        return true;
    }

    private boolean canLoadNext() {
        if (this.endPull == null || !this.endInput.getAsBoolean()) return false;
        EndStatus status = this.endStatus.get();
        return status == EndStatus.MORE || status == EndStatus.FAILED;
    }

    /**
     * 垂直等高列表的快速路径。歌曲行都是固定高度时，不需要每帧给几千个子项重新排版；
     * 只维护当前视口附近的子项，其余子项放到裁剪区外。
     */
    private double uniformVerticalItemHeight = -1.0;
    /**
     * 上一帧排版完成时的「布局签名」。签名一致就整段跳过重排。
     *
     * <p>为什么需要它：{@code VERTICAL_WITH_HORIZONTAL_FILL}（主页网格就是这种）每帧要扫两遍
     * 全部子项（算内容高度、再摆位置）。240 张卡时这是 480 次尺寸读取 —— 静止时纯属白烧，
     * 而内置「播放器大小」缩放期间面板尺寸每帧都在变，这段开销正好压在掉帧最敏感的时刻。</p>
     *
     * <p>签名只含「会让排版结果改变」的量：滚动偏移、视口宽高、子项数、以及子项尺寸的指纹。
     * 子项尺寸指纹由 {@link #invalidateLayout()} 显式失效 —— 卡片宽变了由 HomePanel 调用，
     * 比每帧遍历子项求和便宜得多。</p>
     */
    private long layoutSignature = Long.MIN_VALUE;
    /** 子项尺寸的指纹：只在调用方显式失效时改变。 */
    private long contentFingerprint;
    /** 是否启用「签名相同就跳过重排」的快路径（只有显式 invalidateLayout 过的容器才启用）。 */
    private boolean layoutSignatureEnabled;
    private int uniformFirstVisible = -1;
    private int uniformLastVisible = -1;
    /** Visible object identities let list replacement stay O(visible) without leaving old rows behind. */
    private final Set<AbstractWidget<?>> uniformVisibleChildren =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<AbstractWidget<?>> uniformNextVisibleChildren =
            Collections.newSetFromMap(new IdentityHashMap<>());
    /** Ordered candidates include the same one-row overscan used by uniform layout. */
    private List<AbstractWidget<?>> uniformRenderChildren = Collections.emptyList();

    /** 显式失效的纵向/网格布局保存内容坐标；滚动只更新可见窗口，不重排整张列表。 */
    private boolean indexedVerticalLayout;
    private List<LayoutSlot> indexedLayout = Collections.emptyList();
    private double indexedContentLength;

    private static final class LayoutSlot {
        final AbstractWidget<?> child;
        final double x, y;
        double end;

        LayoutSlot(AbstractWidget<?> child, double x, double y, double end) {
            this.child = child;
            this.x = x;
            this.y = y;
            this.end = end;
        }
    }

    @Getter
    private Alignment alignment = Alignment.VERTICAL;

    /**
     * 子组件的排列方式
     */
    public enum Alignment {
        /**
         * 仅垂直排列 (默认)
         */
        VERTICAL,
        /**
         * 仅水平排列
         */
        HORIZONTAL,
        /**
         * 垂直排列并水平填充
         */
        VERTICAL_WITH_HORIZONTAL_FILL
    }

    public ScrollPanel setAlignment(Alignment alignment) {

        if (alignment == null)
            throw new IllegalArgumentException("Alignment cannot be null!");

        this.alignment = alignment;
        this.resetUniformLayoutState();
        return this;
    }

    /**
     * 让滚动指示条画在视口<b>内侧</b>（默认画在外侧留白里）。
     *
     * <p>页面级列表（主页网格、热搜、私人 FM 历史）左右各有 12L 页面边距，指示条住在视口右侧
     * 那条留白里，既不压内容也不占内容宽度 —— 内容本身是精确填满视口宽的。但浮层（NcmSheet
     * 的整页滚动区）宽度就等于弹窗宽度，外侧没有留白可住，画出去会贴住弹窗圆角，那类列表
     * 必须显式回到内侧。</p>
     */
    public ScrollPanel setIndicatorInsideViewport() {
        this.indicatorInsideViewport = true;
        return this;
    }

    /** Enable direct manipulation. The input host owns the pointer from press through release. */
    public ScrollPanel enableScrollbarDrag() {
        this.scrollbar = new VerticalScroll();
        return this;
    }

    public boolean pressScrollbar(double x, double y) {
        if (this.scrollbar == null || this.isHidden() || !this.canPollTouchInput()
                || this.indicatorAlpha <= .015f || !this.hitsThumb(x, y)) return false;
        int track = this.scrollbarTrackHeight();
        if (track <= this.scrollbar.thumbHeight(track)) return false;
        if (!this.scrollbar.beginDrag(y - this.getY(), track)) return false;
        this.touchDragging = false;
        this.applyScrollbarOffset();
        return true;
    }

    public boolean dragScrollbar(double y) {
        if (this.scrollbar == null || !this.scrollbar.dragging()) return false;
        this.scrollbar.dragTo(y - this.getY(), this.scrollbarTrackHeight());
        this.applyScrollbarOffset();
        return true;
    }

    public void endScrollbarDrag() {
        if (this.scrollbar != null) this.scrollbar.endDrag();
    }

    public void cancelScrollbarDrag() {
        if (this.scrollbar != null && this.scrollbar.cancelDrag()) this.applyScrollbarOffset();
    }

    private void applyScrollbarOffset() {
        this.targetScrollOffset = this.actualScrollOffset = this.scrollbar.offset();
        this.lastScrollInputNanos = System.nanoTime();
    }

    private int scrollbarTrackHeight() {
        return Math.max(0, (int) this.getHeight());
    }

    private double indicatorCenterX() {
        return this.getX() + this.getWidth()
                + (this.indicatorInsideViewport ? -INDICATOR_INSET_INSIDE : INDICATOR_INSET_RIGHT);
    }

    private boolean hitsThumb(double x, double y) {
        return Math.abs(x - this.indicatorCenterX()) <= 4.5
                && y >= this.indicatorThumbTop && y < this.indicatorThumbTop + this.indicatorThumbLength;
    }

    /**
     * 为固定高度的垂直条目启用排版快速路径。传入非正数可关闭。
     * 隐藏/筛选条目时应关闭此模式，因为隐藏项会改变实际行高。
     */
    public ScrollPanel setUniformVerticalItemHeight(double height) {
        this.uniformVerticalItemHeight = height > 0.0 ? height : -1.0;
        if (this.hasIndexedVerticalLayout()) {
            // The indexed pass needs the previous visible children to clear hover and
            // finish animations for rows that disappear when a filter is applied.
            this.invalidateLayout();
            return this;
        }
        this.resetUniformLayoutState();
        if (this.uniformVerticalItemHeight > 0.0) {
            this.placeUniformChildrenOutsideViewport();
        }
        return this;
    }

    private boolean hasUniformVerticalLayout() {
        return this.alignment == Alignment.VERTICAL && this.uniformVerticalItemHeight > 0.0;
    }

    public ScrollPanel setIndexedVerticalLayout() {
        this.indexedVerticalLayout = true;
        this.invalidateLayout();
        return this;
    }

    private boolean hasIndexedVerticalLayout() {
        return this.indexedVerticalLayout && !this.hasUniformVerticalLayout()
                && this.alignment != Alignment.HORIZONTAL;
    }

    @Override
    protected List<AbstractWidget<?>> getRenderChildren() {
        return this.hasUniformVerticalLayout() || this.hasIndexedVerticalLayout()
                ? this.uniformRenderChildren : super.getRenderChildren();
    }

    private void resetUniformLayoutState() {
        this.layoutSignature = Long.MIN_VALUE;
        this.indexedLayout = Collections.emptyList();
        this.uniformFirstVisible = -1;
        this.uniformLastVisible = -1;
        this.uniformVisibleChildren.clear();
        this.uniformNextVisibleChildren.clear();
        this.uniformRenderChildren = Collections.emptyList();
    }

    private double uniformContentLength() {
        int count = this.getChildren().size();
        if (count <= 0) return 0.0;
        return count * this.uniformVerticalItemHeight + Math.max(0, count - 1) * this.spacing;
    }

    private void placeUniformChildrenOutsideViewport() {
        this.placeUniformChildrenOutsideViewport(this.getChildren());
    }

    private void placeUniformChildrenOutsideViewport(List<AbstractWidget<?>> children) {
        for (AbstractWidget<?> child : children) {
            child.clearViewportHover();
            child.onViewportExit();
            child.setPosition(child.getRelativeX(), OUTSIDE_VIEWPORT_Y);
        }
    }

    private void placeUniformChildrenOutsideViewport(AbstractWidget<?>[] children) {
        for (AbstractWidget<?> child : children) {
            child.clearViewportHover();
            child.onViewportExit();
            child.setPosition(child.getRelativeX(), OUTSIDE_VIEWPORT_Y);
        }
    }


    @Override
    public void addChild(AbstractWidget<?>... children) {
        super.addChild(children);
        if (this.hasUniformVerticalLayout()) {
            this.placeUniformChildrenOutsideViewport(children);
        }
    }

    @Override
    public void addChild(List<AbstractWidget<?>> children) {
        super.addChild(children);
        if (this.hasUniformVerticalLayout()) {
            this.placeUniformChildrenOutsideViewport(children);
        }
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        // 触屏拖动滚动（必须在 clamp/推进之前：拖动本身就要写 target 与 actual）。
        this.advanceTouchDrag(mouseX, mouseY);

        // 以前这一帧会为 clamp、actual 再 clamp、align 各自扫描一遍全部子项。
        // 歌单几百/几千行时，滚轮期间这类重复 O(n) 会和可见行绘制叠在一起。
        // 等高歌曲行走固定高度快速路径，只计算数量并更新视口附近的行。
        if (this.hasUniformVerticalLayout()) {
            double maximumScrollOffset = Math.max(0.0, this.uniformContentLength() - this.getHeight());
            this.clampScrollOffset(maximumScrollOffset);
            this.actualScrollOffset = this.advanceScrollOffset(this.actualScrollOffset, this.targetScrollOffset);
            this.actualScrollOffset = Math.max(0, Math.min(this.actualScrollOffset, maximumScrollOffset));
            this.updateScrollIndicator(maximumScrollOffset, mouseX, mouseY);
            this.alignUniformChildren();
            return;
        }

        if (this.hasIndexedVerticalLayout()) {
            this.ensureIndexedLayout();
            double maximumScrollOffset = Math.max(0.0, this.indexedContentLength - this.getHeight());
            this.clampScrollOffset(maximumScrollOffset);
            this.actualScrollOffset = this.advanceScrollOffset(this.actualScrollOffset, this.targetScrollOffset);
            this.actualScrollOffset = Math.max(0, Math.min(this.actualScrollOffset, maximumScrollOffset));
            this.updateScrollIndicator(maximumScrollOffset, mouseX, mouseY);
            this.alignIndexedChildren();
            return;
        }

        // 未声明显式失效的容器保留通用排版，子项仍可在渲染回调里动态改变尺寸。
        if (this.layoutSignatureEnabled) {
            final long signature = layoutSignature(this.getWidth(), this.getHeight(),
                    this.getChildren().size(), this.contentFingerprint, this.actualScrollOffset,
                    this.targetScrollOffset);
            if (signature == this.layoutSignature) {
                this.updateScrollIndicator(this.indicatorMaximumOffset, mouseX, mouseY);
                return;
            }
            this.layoutSignature = signature;
        }

        double maximumScrollOffset = this.getMaximumScrollOffset();
        this.clampScrollOffset(maximumScrollOffset);
        this.actualScrollOffset = this.advanceScrollOffset(this.actualScrollOffset, this.targetScrollOffset);
        this.actualScrollOffset = Math.max(0, Math.min(this.actualScrollOffset, maximumScrollOffset));
        this.updateScrollIndicator(maximumScrollOffset, mouseX, mouseY);

        // 设置子组件的垂直位置
        this.alignChildren(maximumScrollOffset);
    }

    @Override
    protected void renderDebugLayout() {
        super.renderDebugLayout();
        Rect.draw(this.getX(), this.getY(), this.getWidth(), this.getHeight(), reAlpha(0x00F50EE8, this.getAlpha() * .05f));
    }

    public ScrollPanel setSpacing(double spacing) {
        this.spacing = spacing;
        return this;
    }

    /**
     * 设置每个滚轮刻度移动的距离。内容较长的面板可以提高该值，避免用户
     * 需要滚动大量次数才能到达末尾。
     */
    public ScrollPanel setScrollStrength(double scrollStrength) {
        this.scrollStrength = Math.max(1, scrollStrength);
        return this;
    }

    /**
     * 把「会影响排版结果」的几个量折成一个签名。
     *
     * <p>只用加减乘与位运算，不分配对象、不遍历子项：这是每帧都要跑一次的判据，
     * 它必须比它省下的那两次全量遍历便宜得多。</p>
     */
    private static long layoutSignature(double width, double height, int childCount,
                                        long contentFingerprint, double actualOffset, double targetOffset) {
        long signature = Double.doubleToLongBits(width);
        signature = signature * 31 + Double.doubleToLongBits(height);
        signature = signature * 31 + childCount;
        signature = signature * 31 + contentFingerprint;
        // 滚动偏移只认「摆位用的那个」：补间进行中 actual 每帧都在变，必须重排。
        signature = signature * 31 + Double.doubleToLongBits(actualOffset);
        signature = signature * 31 + Double.doubleToLongBits(targetOffset);
        return signature;
    }

    /**
     * 让下一帧强制重排。
     *
     * <p>调用时机：子项的尺寸/数量在容器之外被改动了（主页网格的卡片宽随面板缩放而变）。
     * 不调用的话，签名看起来没变，容器会以为上一帧的位置仍然有效而跳过重排 ——
     * 卡片就会挤在旧的行宽上。这比每帧遍历子项求和便宜得多。</p>
     *
     * <p><b>只有显式调用过本方法的容器才会启用「签名相同就跳过重排」。</b>
     * 其它容器（弹窗列表、搜索结果等）的子项宽度常常依赖父级宽度、在渲染回调里自行改动，
     * 容器无从得知，沿用原来的每帧重排才安全 —— 这条快路径只服务于
     * 「尺寸变化完全由宿主显式驱动」的主页网格。</p>
     */
    public void invalidateLayout() {
        this.contentFingerprint++;
        this.layoutSignature = Long.MIN_VALUE;
        this.layoutSignatureEnabled = true;
    }

    @Override
    public boolean onDWheel(double mouseX, double mouseY, int dWheel) {
        if (this.scrollbar != null && this.scrollbar.dragging()) return true;

        // 垂直滑动
        if (dWheel != 0) this.lastScrollInputNanos = System.nanoTime();
        this.performScroll(dWheel);
        super.onDWheel(mouseX, mouseY, dWheel);

        return true;
    }

    @Override
    public boolean onKeyTyped(char character, int keyCode) {
        if (keyCode != Keyboard.KEY_END || !canLoadNext()) return super.onKeyTyped(character, keyCode);
        double end = this.getMaximumScrollOffset();
        if (this.actualScrollOffset >= end - .5) this.loadNext.run();
        else this.scrollToOffsetImmediately(end);
        return true;
    }

    // ──────────────────────── 触屏拖动滚动 ────────────────────────

    /** 本轮纵向拖动是否由本容器认领。 */
    private boolean touchDragging;

    /** 甩动的最小速度（GUI 像素/ms）：低于它的松手只停住，不给惯性。 */
    private static final double TOUCH_FLING_MIN_VELOCITY = 0.05;

    /**
     * 触屏拖动滚动：认领 → 1:1 跟手 → 松手惯性甩动。
     *
     * <h2>认领条件（全部满足才动手）</h2>
     * <ul>
     *   <li>手势层处于<b>纵向拖动</b>（位移超阈值且主轴锁定为纵）；</li>
     *   <li>尚无人认领——多个滚动容器同屏时（侧栏 + 页面列表）按下起点落在谁家谁先到先得，
     *       渲染顺序即查询顺序，不重叠就不冲突；</li>
     *   <li>按下起点在本视口内（用 {@link #testHovered}，与悬停同一套几何）；</li>
     *   <li>纵向排版且内容长于视口（{@code maxOffset > 0}，没有滚动余量的容器不抢手势）。</li>
     * </ul>
     *
     * <h2>符号表（与滚轮严格一致）</h2>
     * 偏移 0 = 顶部；滚轮向上（dWheel&gt;0）= {@code target -= strength}。手指<b>向下</b>滑
     * （dy&gt;0）内容跟手下移、视口走向顶部 = {@code target -= dy}；向上甩
     * （velocityY&lt;0）视口走向底部 = {@code target -= signum(v)·distance}。
     *
     * <h2>为什么拖动期间 actual 直接等于 target</h2>
     * 42ms 的指数平滑是给滚轮/甩动这种「目标突变」用的减速带；拖动要求 1:1 跟手，
     * 平滑会变成橡皮筋。甩动时把惯性距离一次性记进 target，让既有平滑自然接管减速尾。</h2>
     */
    private void advanceTouchDrag(double mouseX, double mouseY) {
        if (this.scrollbar != null && this.scrollbar.dragging()) return;
        if (!canPollTouchInput() || this.endPull != null && !this.endInput.getAsBoolean()) {
            // A modal change must not finish an underlying drag with somebody else's release/fling.
            this.touchDragging = false;
            if (this.endPull != null) this.endPull.reset();
            return;
        }
        WidgetTouch gestures = WidgetTouch.Host.get();
        if (!gestures.enabled()) {
            this.touchDragging = false;
            if (this.endPull != null && this.endPull.progress() > 0) this.endPull.reset();
            return;
        }
        if (this.touchDragging) {
            if (gestures.verticalDrag() && gestures.claimedBy() == this) {
                double dy = this.endPull == null ? gestures.dy() : mouseY - this.lastTouchY;
                this.lastTouchY = mouseY;
                if (dy != 0) {
                    this.dragTouch(dy);
                }
                return;
            }
            // 松手（或认领被抢走的异常帧）：结算甩动。
            this.touchDragging = false;
            if (this.endPull != null) {
                boolean released = !gestures.pressed() && gestures.lastPressDragged();
                boolean allowed = released && this.testHovered(mouseX, mouseY) && this.canLoadNext();
                if (allowed) this.dragTouch(mouseY - this.lastTouchY);
                boolean pulled = this.endPull.progress() > 0;
                if (this.endPull.release(allowed, System.nanoTime() / 1_000_000L)) this.loadNext.run();
                if (!released || pulled) return;
            }
            if (!gestures.pressed()) {
                double velocity = gestures.velocityY();
                if (Math.abs(velocity) > TOUCH_FLING_MIN_VELOCITY) {
                    double distance = Math.min(gestures.flingMaxDistance(),
                            (velocity * velocity) / (2.0 * gestures.flingDeceleration()));
                    double maximum = this.maximumOffsetForTouch();
                    this.targetScrollOffset = Math.max(0, Math.min(
                            this.targetScrollOffset - Math.signum(velocity) * distance, maximum));
                    this.lastScrollInputNanos = System.nanoTime();
                }
            }
            return;
        }
        if (gestures.verticalDrag()
                && gestures.claimedBy() == null
                && this.alignment != Alignment.HORIZONTAL
                && (this.maximumOffsetForTouch() > 0.0 || this.canLoadNext())
                && this.testHovered(gestures.startX(), gestures.startY())) {
            this.touchDragging = gestures.claim(this);
            if (this.touchDragging && this.endPull != null) {
                this.endPull.reset();
                this.lastTouchY = mouseY;
                this.targetScrollOffset = this.actualScrollOffset;
                this.dragTouch(mouseY - gestures.startY());
            }
        }
    }

    private void dragTouch(double dy) {
        if (!Double.isFinite(dy)) return;
        double maximum = this.maximumOffsetForTouch();
        if (this.canLoadNext()) {
            this.targetScrollOffset = this.endPull.drag(this.targetScrollOffset, maximum, -dy);
        } else {
            if (this.endPull != null) this.endPull.reset();
            this.targetScrollOffset = Math.max(0, Math.min(this.targetScrollOffset - dy, maximum));
        }
        this.actualScrollOffset = this.targetScrollOffset;
        this.lastScrollInputNanos = System.nanoTime();
    }

    /** 当前内容最大可滚偏移；认领判据与拖动期间都要，直接复用滚轮那条路径的算法。 */
    private double maximumOffsetForTouch() {
        return this.getMaximumScrollOffset();
    }

    /**
     * 直接喂一次滚轮，不经过命中派发。
     *
     * <p>给「指针在页面页头/空白处，但滚轮应该滚主列表」这种转发用：内容页拿到未消费的滚轮后
     * 调它，语义与用户把鼠标移进列表滚动一致（同样记下滚动时间、同样夹住目标偏移）。</p>
     */
    public void forwardWheel(int dWheel) {
        if (dWheel == 0) return;
        this.lastScrollInputNanos = System.nanoTime();
        this.performScroll(dWheel);
    }

    /** 在滚轮后的短窗口内认为用户正在快速浏览，不为刚进入视口的条目叠加动画。 */
    public boolean isScrollActive() {
        return this.lastScrollInputNanos != 0L
                && System.nanoTime() - this.lastScrollInputNanos < 140_000_000L;
    }

    private void performScroll(int dWheel) {

        if (dWheel != 0) {
            if (dWheel < 0 && this.canLoadNext() && !this.touchDragging
                    && this.actualScrollOffset >= this.getMaximumScrollOffset() - .5) {
                this.loadNext.run();
                return;
            }

            double strength = this.scrollStrength;

            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT))
                strength *= 2;

            if (dWheel > 0)
                this.targetScrollOffset -= strength;
            else
                this.targetScrollOffset += strength;
        }

        this.clampScrollOffset();
    }

    private void clampScrollOffset() {
        this.clampScrollOffset(this.getMaximumScrollOffset());
    }

    private void clampScrollOffset(double maximumScrollOffset) {
        this.targetScrollOffset = Math.max(0, Math.min(this.targetScrollOffset, maximumScrollOffset));
    }

    private double getMaximumScrollOffset() {
        if (this.hasUniformVerticalLayout()) {
            return Math.max(0.0, this.uniformContentLength() - this.getHeight());
        }
        if (this.hasIndexedVerticalLayout()) {
            this.ensureIndexedLayout();
            return Math.max(0.0, this.indexedContentLength - this.getHeight());
        }

        double contentLength;
        switch (this.alignment) {
            case HORIZONTAL:
                contentLength = this.getChildrenWidthSum();
                return Math.max(0, contentLength - this.getWidth());
            case VERTICAL_WITH_HORIZONTAL_FILL:
                contentLength = this.getChildrenHeightSumHorizontalFill();
                return Math.max(0, contentLength - this.getHeight());
            case VERTICAL:
            default:
                contentLength = this.getChildrenHeightSum();
                return Math.max(0, contentLength - this.getHeight());
        }
    }

    public void scrollToEnd() {
        switch (this.alignment) {
            case VERTICAL: {
                double childrenHeightSum = this.getChildrenHeightSum();
                if (childrenHeightSum > this.getHeight())
                    this.targetScrollOffset = childrenHeightSum - this.getHeight();
                else
                    this.targetScrollOffset = 0;
                break;
            }
            case HORIZONTAL: {
                double childrenWidthSum = this.getChildrenWidthSum();
                if (childrenWidthSum > this.getWidth())
                    this.targetScrollOffset = childrenWidthSum - this.getWidth();
                else
                    this.targetScrollOffset = 0;
                break;
            }
            case VERTICAL_WITH_HORIZONTAL_FILL: {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
                if (childrenHeightSum > this.getHeight())
                    this.targetScrollOffset = childrenHeightSum - this.getHeight();
                else
                    this.targetScrollOffset = 0;
                break;
            }
        }
    }

    /**
     * 立即滚到内容末尾，跳过滚动补间。给「打开浮层的那一帧就要看到底部」的场景用：
     * {@link #scrollToEnd()} 只设目标值，actual 还要好几帧才追上，那几帧里排在视口外的
     * 行仍然持有越过下沿的绝对坐标。
     */
    public void scrollToEndImmediately() {
        switch (this.alignment) {
            case VERTICAL: {
                double childrenHeightSum = this.getChildrenHeightSum();
                if (childrenHeightSum > this.getHeight())
                    this.actualScrollOffset = childrenHeightSum - this.getHeight();
                else
                    this.actualScrollOffset = 0;
                break;
            }
            case HORIZONTAL: {
                double childrenWidthSum = this.getChildrenWidthSum();
                if (childrenWidthSum > this.getWidth())
                    this.actualScrollOffset = childrenWidthSum - this.getWidth();
                else
                    this.actualScrollOffset = 0;
                break;
            }
            case VERTICAL_WITH_HORIZONTAL_FILL: {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
                if (childrenHeightSum > this.getHeight())
                    this.actualScrollOffset = childrenHeightSum - this.getHeight();
                else
                    this.actualScrollOffset = 0;
                break;
            }
        }
    }

    /**
     * 立即把滚动偏移钉到指定值，actual 与 target 一起设、不经过补间，并夹进当帧的内容范围。
     *
     * <p>给「打开浮层的那一帧就要让某一行落进视口」用：接力飞行板的落点坐标每帧现读，
     * 若靠 target 补间追过去，追的那几帧里落点还在视口下方，板子就朝屏幕外飞。夹取读的是
     * 子项当帧的尺寸，所以要在子项排完版之后再调（首次渲染的 afterRenderChildren 里正合适）。</p>
     */
    public void scrollToOffsetImmediately(double offset) {
        double maximum = this.getMaximumScrollOffset();
        this.targetScrollOffset = Math.max(0, Math.min(offset, maximum));
        this.actualScrollOffset = this.targetScrollOffset;
    }

    public boolean isScrolledToEnd() {
        if (this.alignment == Alignment.VERTICAL) {
            double childrenHeightSum = this.getChildrenHeightSum();
            if (childrenHeightSum > this.getHeight())
                return this.targetScrollOffset >= childrenHeightSum - this.getHeight();
            else
                return this.targetScrollOffset == 0;
        } else if (this.alignment == Alignment.HORIZONTAL) {
            double childrenWidthSum = this.getChildrenWidthSum();
            if (childrenWidthSum > this.getWidth())
                return this.targetScrollOffset >= childrenWidthSum - this.getWidth();
            else
                return this.targetScrollOffset == 0;
        } else {
            double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
            if (childrenHeightSum > this.getHeight())
                return this.targetScrollOffset >= childrenHeightSum - this.getHeight();
            else
                return this.targetScrollOffset == 0;
        }
    }

    public void alignChildren() {
        if (this.hasUniformVerticalLayout()) {
            this.alignUniformChildren();
            return;
        }
        if (this.hasIndexedVerticalLayout()) {
            this.ensureIndexedLayout();
            this.alignIndexedChildren();
            return;
        }
        this.alignChildren(this.getMaximumScrollOffset());
    }

    private void alignUniformChildren() {
        List<AbstractWidget<?>> children = this.getChildren();
        int count = children.size();
        if (count == 0) {
            for (AbstractWidget<?> child : this.uniformVisibleChildren) {
                child.clearViewportHover();
                child.onViewportExit();
            }
            this.resetUniformLayoutState();
            return;
        }

        double stride = this.uniformVerticalItemHeight + this.spacing;
        int first = Math.max(0, (int) Math.floor(this.actualScrollOffset / stride) - 1);
        int last = Math.min(count - 1,
                (int) Math.ceil((this.actualScrollOffset + Math.max(0.0, this.getHeight())) / stride) + 1);

        // Build the next visible identity set first. This handles fast jumps and arbitrary list
        // replacement without scanning every child or relying on stale integer indices.
        this.uniformNextVisibleChildren.clear();
        boolean sameWindow = this.uniformRenderChildren.size() == Math.max(0, last - first + 1);
        for (int index = first; index <= last; index++) {
            AbstractWidget<?> child = children.get(index);
            this.uniformNextVisibleChildren.add(child);
            if (sameWindow && this.uniformRenderChildren.get(index - first) != child) sameWindow = false;
        }
        // Keep the current render iteration stable if a child callback resets the panel.
        if (!sameWindow) {
            List<AbstractWidget<?>> next = new ArrayList<>(Math.max(0, last - first + 1));
            for (int index = first; index <= last; index++) next.add(children.get(index));
            this.uniformRenderChildren = next;
        }

        for (AbstractWidget<?> child : this.uniformVisibleChildren) {
            if (!this.uniformNextVisibleChildren.contains(child)) {
                child.clearViewportHover();
                child.onViewportExit();
                child.setPosition(child.getRelativeX(), OUTSIDE_VIEWPORT_Y);
            }
        }

        for (int index = first; index <= last; index++) {
            AbstractWidget<?> child = children.get(index);
            child.setPosition(child.getRelativeX(), index * stride - this.actualScrollOffset);
        }

        this.uniformVisibleChildren.clear();
        this.uniformVisibleChildren.addAll(this.uniformNextVisibleChildren);
        this.uniformNextVisibleChildren.clear();
        this.uniformFirstVisible = first;
        this.uniformLastVisible = last;
    }

    private void ensureIndexedLayout() {
        long signature = layoutSignature(this.getWidth(), 0.0, this.getChildren().size(),
                this.contentFingerprint, this.spacing, this.alignment.ordinal());
        if (signature == this.layoutSignature) return;

        List<LayoutSlot> slots = new ArrayList<>(this.getChildren().size());
        double x = 0.0, y = 0.0, rowHeight = 0.0;
        int rowStart = 0;
        for (AbstractWidget<?> child : this.getChildren()) {
            child.setPosition(child.getRelativeX(), OUTSIDE_VIEWPORT_Y);
            if (child.isHidden()) continue;
            double width = child.getWidth();
            double height = child.getHeight();
            if (this.alignment == Alignment.VERTICAL) {
                slots.add(new LayoutSlot(child, child.getRelativeX(), y, y + height));
                y += height + this.spacing;
            } else {
                if (x > 0.0 && x + width > this.getWidth()) {
                    for (int i = rowStart; i < slots.size(); i++) slots.get(i).end = y + rowHeight;
                    rowStart = slots.size();
                    x = 0.0;
                    y += rowHeight + this.spacing;
                    rowHeight = 0.0;
                }
                slots.add(new LayoutSlot(child, x, y, y + height));
                x += width + this.spacing;
                rowHeight = Math.max(rowHeight, height);
            }
        }
        if (this.alignment == Alignment.VERTICAL_WITH_HORIZONTAL_FILL) {
            for (int i = rowStart; i < slots.size(); i++) slots.get(i).end = y + rowHeight;
            this.indexedContentLength = slots.isEmpty() ? 0.0 : y + rowHeight;
        } else {
            this.indexedContentLength = slots.isEmpty() ? 0.0 : Math.max(0.0, y - this.spacing);
        }
        this.indexedLayout = slots;
        this.layoutSignature = signature;
    }

    private void alignIndexedChildren() {
        List<LayoutSlot> slots = this.indexedLayout;
        double top = this.actualScrollOffset;
        double bottom = top + Math.max(0.0, this.getHeight());
        int low = 0, high = slots.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (slots.get(mid).end < top) low = mid + 1;
            else high = mid;
        }
        int first = low;
        high = slots.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (slots.get(mid).y <= bottom) low = mid + 1;
            else high = mid;
        }
        int end = low;

        this.uniformNextVisibleChildren.clear();
        boolean sameWindow = this.uniformRenderChildren.size() == end - first;
        for (int i = first; i < end; i++) {
            AbstractWidget<?> child = slots.get(i).child;
            this.uniformNextVisibleChildren.add(child);
            if (sameWindow && this.uniformRenderChildren.get(i - first) != child) sameWindow = false;
        }
        if (!sameWindow) {
            List<AbstractWidget<?>> next = new ArrayList<>(end - first);
            for (int i = first; i < end; i++) next.add(slots.get(i).child);
            this.uniformRenderChildren = next;
        }
        for (AbstractWidget<?> child : this.uniformVisibleChildren) {
            if (!this.uniformNextVisibleChildren.contains(child)) {
                child.clearViewportHover();
                child.onViewportExit();
                child.setPosition(child.getRelativeX(), OUTSIDE_VIEWPORT_Y);
            }
        }
        for (int i = first; i < end; i++) {
            LayoutSlot slot = slots.get(i);
            slot.child.setPosition(slot.x, slot.y - top);
        }
        this.uniformVisibleChildren.clear();
        this.uniformVisibleChildren.addAll(this.uniformNextVisibleChildren);
        this.uniformNextVisibleChildren.clear();
    }

    private void alignChildren(double maximumScrollOffset) {
        double offsetX = 0;
        double offsetY = 0;
        double rowHeight = 0;

        if (this.alignment == Alignment.VERTICAL || this.alignment == Alignment.VERTICAL_WITH_HORIZONTAL_FILL)
            offsetY = -this.actualScrollOffset;
        else
            offsetX = -this.actualScrollOffset;

        // maxScrollOffset 已经在本帧计算过，避免在这里再次扫描全部子项。
        this.targetScrollOffset = Math.max(0, Math.min(this.targetScrollOffset, maximumScrollOffset));

        for (AbstractWidget<?> child : this.getChildren()) {

            double width = child.getWidth();
            double height = child.getHeight();

            if (child.isHidden())
                continue;

            switch (this.alignment) {
                case VERTICAL: {
                    child.setPosition(child.getRelativeX(), offsetY);
                    offsetY += height + spacing;
                    break;
                }
                case HORIZONTAL: {
                    child.setPosition(offsetX, child.getRelativeY());
                    offsetX += width + spacing;
                    break;
                }
                case VERTICAL_WITH_HORIZONTAL_FILL: {
                    // 当子组件比极小视口更宽时，不要创建空的第一行。
                    // 对于混合大小的控件，按已完成行中最高的项
                    // 推进，而不是按恰好换行的那个项。
                    if (offsetX > 0 && offsetX + width > this.getWidth()) {
                        offsetX = 0;
                        offsetY += rowHeight + spacing;
                        rowHeight = 0;
                    }

                    child.setPosition(offsetX, offsetY);
                    offsetX += width + spacing;
                    rowHeight = Math.max(rowHeight, height);
                    break;
                }
            }
        }
    }

    protected double getChildrenHeightSum() {
        double result = 0;

        List<AbstractWidget<?>> children = this.getChildren();

        for (AbstractWidget<?> child : children) {

            double width = child.getWidth();
            double height = child.getHeight();

            if (child.isHidden())
                continue;

            result += height + this.spacing;
        }

        if (result > 0)
            result -= this.spacing;

        return result;
    }

    protected double getChildrenWidthSum() {
        double result = 0;

        List<AbstractWidget<?>> children = this.getChildren();

        for (AbstractWidget<?> child : children) {

            double width = child.getWidth();
            double height = child.getHeight();

            if (child.isHidden())
                continue;

            result += width + this.spacing;
        }

        if (result > 0)
            result -= this.spacing;

        return result;
    }

    protected double getChildrenHeightSumHorizontalFill() {
        double result = 0;
        double offsetX = 0;
        double rowHeight = 0;

        List<AbstractWidget<?>> children = this.getChildren();

        for (AbstractWidget<?> child : children) {

            double width = child.getWidth();
            double height = child.getHeight();

            if (child.isHidden())
                continue;

            if (offsetX > 0 && offsetX + width > this.getWidth()) {
                result += rowHeight + spacing;
                offsetX = 0;
                rowHeight = 0;
            }

            offsetX += width + spacing;
            rowHeight = Math.max(rowHeight, height);
        }

        if (rowHeight > 0) {
            result += rowHeight;
        }

        return result;
    }

    @Override
    protected void beforeRenderChildren(double mouseX, double mouseY) {
        // 模板缓冲提供普通的组件裁剪，而投影裁剪矩形是一个
        // 硬屏幕空间边界。圆角封面渲染可能打开嵌套模板
        // 层级，但它无法再逃逸到标题栏上方或控制栏下方。
        ScissorClipManager.begin(this.getX(), this.getY(), this.getWidth(), this.getHeight());
        StencilClipManager.beginClip(() ->
                Rect.draw(this.getX(), this.getY(), this.getWidth(), this.getHeight(), -1));
    }

    @Override
    protected void afterRenderChildren(double mouseX, double mouseY) {
        try {
            this.renderEndPull(mouseX, mouseY);
        } finally {
            try {
                StencilClipManager.endClip();
            } finally {
                ScissorClipManager.end();
            }
        }
        // 指示条画在裁剪之后：它属于「视口边缘的提示」，不该被自己的裁剪吃掉 ——
        // 正因如此它才能住在视口外侧的页面留白里（见 INDICATOR_INSET_RIGHT）。
        // 纵向位置仍被 onRender 夹在视口高度内，不会越界。
        this.drawScrollIndicator();
    }

    private void renderEndPull(double mouseX, double mouseY) {
        if (this.endPull == null || FontManager.pf12 == null || this.getHeight() < 18) return;
        EndStatus status = this.endStatus.get();
        if (status == EndStatus.HIDDEN || !this.endInput.getAsBoolean()) return;
        var preferences = UiPreferences.configured();
        boolean still = preferences.reducedMotion() || preferences.highContrast();
        float progress = this.endPull.progress();
        float reveal = this.endPull.reveal(System.nanoTime() / 1_000_000L, still);
        boolean hovered = this.testHovered(mouseX, mouseY);
        if (progress > 0 && !hovered) return;
        boolean atEnd = this.actualScrollOffset >= this.indicatorMaximumOffset - .5;
        if (reveal == 0 && status != EndStatus.LOADING && (!atEnd || !hovered)) return;
        String message = switch (status) {
            case LOADING -> "continuation.loading";
            case FAILED -> "continuation.retry";
            case END -> "continuation.end";
            default -> progress >= 1 ? "continuation.release"
                    : progress > 0 ? "continuation.pull_further"
                    : WidgetTouch.Host.get().enabled() ? "continuation.pull_up" : "continuation.scroll_end";
        };
        var font = FontManager.pf12;
        double available = this.getWidth() - 16;
        if (available < 20) return;
        String text = Component.translatable("sh_better_ui.online_music." + message).getString();
        if (font.getStringWidth(text) > available - 12) text = font.fitWidth(text, available - 12)[0];
        double width = Math.min(available, font.getStringWidthD(text) + 12);
        double center = this.getX() + this.getWidth() * .5;
        double x = center - width * .5;
        double y = this.getY() + this.getHeight() - 18 - (still ? 0 : reveal * 8);
        y = Math.max(this.getY(), y);
        float opacity = this.getAlpha();
        int ink = WidgetTheme.color(WidgetTheme.Role.PRIMARY_TEXT);
        int fill = WidgetTheme.color(WidgetTheme.Role.INPUT_BACKGROUND);
        this.roundedRect(x, y, width, 16, 5, reAlpha(fill, opacity));
        font.drawCenteredString(text, center, y + (16 - font.getFontHeight()) * .5, reAlpha(ink, opacity));
        if (reveal > 0) Rect.draw(x + 5, y + 14, (width - 10) * reveal, 1, reAlpha(ink, opacity));
    }

    /**
     * 帧率无关的滚动补间。
     *
     * <p>原来的 {@code Interpolations.interpolate(actual, target, 1f)} 是「每帧吃掉 8 成」：60fps 下
     * 两三帧追平，30fps 下就慢一半，240fps 下又过快。这里换成以毫秒为时间常数的指数逼近（τ≈42ms），
     * 保留「跟手、两三帧追平」的观感，同时帧率无关。</p>
     */
    private double advanceScrollOffset(double current, double target) {
        final double frameMs = Math.min(MAX_FRAME_MS, Math.max(0.0, RenderSystem.getFrameDeltaTime() * 10.0));
        if (frameMs <= 0.0) return current;
        final double next = current + (target - current) * (1.0 - Math.exp(-frameMs / 42.0));
        return Math.abs(target - next) < .25 ? target : next;
    }

    /**
     * 每帧维护滚动指示条：内容溢出才出现，滚动/悬停/补间未停时保持，其余时间淡出。
     *
     * <p>只做算术、不分配对象；滑块的屏幕坐标在这里一次算好，绘制阶段不再扫描子项，也不重新
     * 计算内容高度。检测用「最近一次滚轮 + 补间是否还在追 + 指针是否在视口内」三条并集，
     * 用户把鼠标停在列表上时也能看到当前位置。</p>
     */
    private void updateScrollIndicator(double maximumScrollOffset, double mouseX, double mouseY) {
        this.indicatorMaximumOffset = maximumScrollOffset;
        final double viewport = this.getHeight();
        final boolean vertical = this.alignment != Alignment.HORIZONTAL;
        final boolean overflows = vertical && viewport > 1.0 && maximumScrollOffset > .5;
        if (!overflows) {
            this.indicatorAlpha = 0.0f;
            this.endScrollbarDrag();
            return;
        }

        boolean draggable = this.scrollbar != null;
        if (draggable) {
            if (this.scrollbarViewport != viewport || this.scrollbarMaximum != maximumScrollOffset) {
                this.scrollbar.resize(viewport, viewport + maximumScrollOffset);
                this.scrollbarViewport = viewport;
                this.scrollbarMaximum = maximumScrollOffset;
            }
            this.scrollbar.scrollTo(this.actualScrollOffset);
            int track = this.scrollbarTrackHeight();
            this.indicatorThumbLength = this.scrollbar.thumbHeight(track);
            this.indicatorThumbTop = this.getY() + this.scrollbar.thumbTop(track);
        }
        boolean thumbHovered = draggable && this.canPollTouchInput() && this.hitsThumb(mouseX, mouseY);
        boolean dragging = draggable && this.scrollbar.dragging();
        if (thumbHovered || dragging) CursorUtils.setOverride(CursorUtils.HAND);
        final long now = System.nanoTime();
        final boolean recentlyScrolled = this.lastScrollInputNanos != 0L
                && now - this.lastScrollInputNanos < INDICATOR_HOLD_NANOS;
        final boolean settling = Math.abs(this.targetScrollOffset - this.actualScrollOffset) > .5;
        final boolean visible = dragging || thumbHovered || recentlyScrolled || settling || this.testHovered(mouseX, mouseY);

        final double frameMs = Math.min(MAX_FRAME_MS, Math.max(0.0, RenderSystem.getFrameDeltaTime() * 10.0));
        if (visible) {
            this.indicatorAlpha = Math.min(1.0f,
                    this.indicatorAlpha + (float) (frameMs * INDICATOR_FADE_IN_PER_MS));
        } else {
            this.indicatorAlpha = Math.max(0.0f,
                    this.indicatorAlpha - (float) (frameMs * INDICATOR_FADE_OUT_PER_MS));
        }

        if (draggable) return;
        final double contentLength = maximumScrollOffset + viewport;
        final double ratio = Math.min(1.0, viewport / Math.max(viewport, contentLength));
        final double length = Math.max(INDICATOR_MIN_LENGTH, viewport * ratio);
        final double travel = Math.max(0.0, viewport - length);
        final double progress = maximumScrollOffset <= 0.0 ? 0.0
                : Math.max(0.0, Math.min(1.0, this.actualScrollOffset / maximumScrollOffset));
        this.indicatorThumbLength = length;
        this.indicatorThumbTop = this.getY() + travel * progress;
    }

    /** 画在视口右缘的滑块；透明度跟主题次要文字色走。 */
    private void drawScrollIndicator() {
        if (this.indicatorAlpha <= .015f || this.indicatorMaximumOffset <= .5) return;
        final int color = WidgetTheme.color(WidgetTheme.Role.SECONDARY_TEXT);
        // 以「中心线」定位：正值向内、负值向外，换方向时不用同时改宽度与内缩两处。
        boolean dragging = this.scrollbar != null && this.scrollbar.dragging();
        final double centerX = this.indicatorCenterX();
        final double x = centerX - INDICATOR_WIDTH * .5;
        this.roundedRect(x, this.indicatorThumbTop, INDICATOR_WIDTH, this.indicatorThumbLength,
                INDICATOR_WIDTH * .5, RenderSystem.reAlpha(color, this.indicatorAlpha * (dragging ? .95f : .55f)));
    }

    @Override
    protected boolean shouldRenderChildren(AbstractWidget<?> child, double mouseX, double mouseY) {

        switch (this.getAlignment()) {
            case VERTICAL:
            case VERTICAL_WITH_HORIZONTAL_FILL:
                if (child.getRelativeY() + child.getHeight() < 0)
                    return false;

                if (child.getRelativeY() > this.getHeight())
                    return false;
                break;
            case HORIZONTAL:
                if (child.getRelativeX() + child.getWidth() < 0)
                    return false;

                if (child.getRelativeX() > this.getWidth())
                    return false;
                break;
        }

        return true;
    }

    @Override
    protected boolean shouldClickChildren(double mouseX, double mouseY) {
//        System.out.println(this.testHovered(mouseX, mouseY));
        return this.testHovered(mouseX, mouseY);
    }

    @Override
    public boolean canBeScrolled() {
        return true;
    }
}

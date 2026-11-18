// Author: dishanhai
package com.dishanhai.sh_better_ui.client.font;

import com.dishanhai.sh_better_ui.client.gui.scaled.MpTypography;
import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Local-only font import. Confirmation grants use of one named local file, not redistribution. */
public final class NativeCustomFontScreen extends Screen {
    interface Backend {
        CompletableFuture<NativeLocalFontService.Result> importConfirmed(Path path);
        CompletableFuture<NativeLocalFontService.Result> restore();
        boolean busy();
        void cancel();
        void close();
    }
    private final Screen parent;
    private Backend backend;
    private String selectedPath = "";
    private Component status = label("native_scope");
    private CompletableFuture<NativeLocalFontService.Result> pending;
    private ConfirmScreen confirmation;
    private EditBox path;
    private AbstractContainerWidget body;
    private Button importButton, restoreButton, back;

    public NativeCustomFontScreen(Screen parent) { this(parent, null); }
    NativeCustomFontScreen(Screen parent, Backend backend) { super(label("title")); this.parent=parent; this.backend=backend; }
    public static Component label(String key) { return Component.translatable("sh_better_ui.custom_font."+key); }
    private boolean busy() { return pending != null || backend != null && backend.busy(); }
    private void ensureBackend() {
        if (backend != null) return;
        var service = NativeLocalFontService.create(minecraft);
        backend = new Backend() {
            public CompletableFuture<NativeLocalFontService.Result> importConfirmed(Path path) { return service.importConfirmed(path); }
            public CompletableFuture<NativeLocalFontService.Result> restore() { return service.restore(); }
            public boolean busy() { return service.busy(); }
            public void cancel() { service.cancel(); }
            public void close() { service.close(); }
        };
    }
    @Override protected void init() {
        double offset=body==null?0:body.scrollAmount();
        boolean focusPath=path!=null && path.isFocused();
        clearWidgets();body=null;path=null;importButton=null;restoreButton=null;
        int contentWidth=Math.max(1,Math.min(420,width-24));
        if(width>=40 && height>78) {
            var content=LinearLayout.vertical().spacing(7);
            content.addChild(new MultiLineTextWidget(label("native_scope"),font).setMaxWidth(contentWidth));
            content.addChild(new MultiLineTextWidget(label("path"),font).setMaxWidth(contentWidth));
            path=new EditBox(font,0,0,contentWidth,20,label("path"));path.setMaxLength(2048);
            path.setValue(selectedPath);path.setResponder(value->selectedPath=value);path.setEditable(!busy());content.addChild(path);
            importButton=content.addChild(Button.builder(label("import"),b->confirmImport()).width(contentWidth).build());
            restoreButton=content.addChild(Button.builder(label("restore"),b->confirmRestore()).width(contentWidth).build());
            content.addChild(new MultiLineTextWidget(status,font).setMaxWidth(contentWidth));
            content.addChild(new MultiLineTextWidget(label("native_sample_label"),font).setMaxWidth(contentWidth));
            content.addChild(new MultiLineTextWidget(MpTypography.text(label("native_sample")),font).setMaxWidth(contentWidth));
            content.addChild(new MultiLineTextWidget(MpTypography.bold(label("native_sample")),font).setMaxWidth(contentWidth));
            var viewport=new ScrollableLayout(minecraft,content,height-64);viewport.setMinWidth(contentWidth);viewport.arrangeElements();
            viewport.setPosition((width-viewport.getWidth())/2,28);viewport.visitWidgets(widget->body=(AbstractContainerWidget)addRenderableWidget(widget));
            body.setScrollAmount(offset);
            if(focusPath){body.setFocused(path);setFocused(body);}
        }
        back=addRenderableWidget(Button.builder(label(busy()?"cancel":"native_back"),b->onClose())
                .bounds(Math.max(0,(width-Math.min(200,width-8))/2),Math.max(0,height-28),Math.max(1,Math.min(200,width-8)),20).build());
        if(importButton!=null)importButton.active=!busy();if(restoreButton!=null)restoreButton.active=!busy();
    }
    private boolean current() { return NativePort.screen(minecraft)==this; }
    void confirmImport() {
        if(!current() || busy())return;
        final Path source;
        try { source=Path.of(selectedPath).toAbsolutePath().normalize();if(selectedPath.isBlank())throw new IllegalArgumentException(); }
        catch(RuntimeException invalid){status=label("native_invalid_path");rebuildWidgets();return;}
        confirm(label("import"),Component.literal(source.toString()).append("\n\n").append(label("license_detail")),()->start(source));
    }
    void confirmRestore() {
        if(!current() || busy())return;
        confirm(label("restore"),label("native_restore_detail"),()->start(null));
    }
    private void confirm(Component title,Component detail,Runnable accepted) {
        var dialog=new ConfirmScreen[1];
        dialog[0]=new ConfirmScreen(answer->{
            if(NativePort.screen(minecraft)!=dialog[0])return;
            NativePort.show(minecraft,this);confirmation=null;
            if(answer && current())accepted.run();
        },title,detail);
        confirmation=dialog[0];
        NativePort.show(minecraft,confirmation);
    }
    private void start(Path source) {
        ensureBackend();status=label("working");
        try { pending=source==null?backend.restore():backend.importConfirmed(source); }
        catch(RuntimeException failure){status=label("failed");}
        rebuildWidgets();
    }
    @Override public void tick() {
        if(pending==null || !pending.isDone())return;
        try {
            status=label(switch(pending.join()) {
                case APPLIED -> "applied";case RESTORED -> "restored";case CANCELLED -> "cancelled";
                case FAILED -> "failed";case RECOVERY_FAILED -> "recovery";
            });
        } catch(RuntimeException failure){status=label("failed");}
        pending=null;if(current())rebuildWidgets();
    }
    @Override public void onFilesDrop(List<Path> paths) {
        if(!current() || busy() || paths.size()!=1)return;
        selectedPath=paths.getFirst().toString();status=label("confirm_file");rebuildWidgets();
    }
    @Override public void onClose() {
        if(!current())return;
        if(busy()){backend.cancel();status=label("cancelling");rebuildWidgets();return;}
        if(backend!=null)backend.close();NativePort.show(minecraft,parent);
    }
    @Override public void removed() {
        if(confirmation==null && backend!=null)backend.close();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial) {
        super.extractRenderState(graphics,mouseX,mouseY,partial);
        graphics.centeredText(font,title,width/2,10,0xffffffff);
    }
    @Override public boolean isPauseScreen(){return false;}
}

package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.BooleanSupplier;
/** Legacy host uses UUK's native local-modal input and render routing. */
final class NowPlayingMenuHost {
    private final UiLocalModal modal;
    NowPlayingMenuHost(PreviewContext host){modal=new UiLocalModal(host.screen(),host.preferences());}
    boolean supported(){return true;}
    void interrupt(){modal.interrupt();}
    void open(UiButton source,Bounds viewport,Component title,List<NowPlayingMenu.Entry> entries,boolean quickHeader,BooleanSupplier valid){
        NowPlayingMenu.open(modal,source,viewport,title,entries,quickHeader,valid);
    }
    void render(UiFrame frame){} // Native local-modal event integration draws above the host.
}

// Author: dishanhai
package com.dishanhai.sh_better_ui.client.font;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.EditBox;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class NativeCustomFontScreenTest {
    @Test void dropOnlyStagesOneFileAndResizePreservesPathWithoutImporting() throws Exception {
        try(var client=NativeTestClient.install()){
            var backend=new FakeBackend();var screen=new NativeCustomFontScreen(null,backend);
            client.client().screen=screen;screen.width=120;screen.height=160;screen.init();
            screen.onFilesDrop(List.of(Path.of("font.ttf")));
            assertEquals("font.ttf",((EditBox)field(screen,"path")).getValue());assertEquals(0,backend.imports);
            var body=(AbstractContainerWidget)field(screen,"body");
            assertTrue(body.getX()>=0 && body.getX()+body.getWidth()<=screen.width);
            body.setScrollAmount(20);screen.width=180;screen.init();
            assertEquals("font.ttf",((EditBox)field(screen,"path")).getValue());
            assertEquals(20,((AbstractContainerWidget)field(screen,"body")).scrollAmount());
            screen.onFilesDrop(List.of(Path.of("one.ttf"),Path.of("two.ttf")));
            assertEquals("font.ttf",((EditBox)field(screen,"path")).getValue());assertEquals(0,backend.imports);
        }
    }
    @Test void blankPathNeverRequestsImport() throws Exception {
        try(var client=NativeTestClient.install()){
            var backend=new FakeBackend();var screen=new NativeCustomFontScreen(null,backend);
            client.client().screen=screen;screen.width=300;screen.height=220;screen.init();screen.confirmImport();
            assertEquals(0,backend.imports);assertSame(screen,client.client().screen);
        }
    }
    @Test void cancellationWaitsForBackendAndStaleCloseCannotNavigate() throws Exception {
        try(var client=NativeTestClient.install()){
            var backend=new FakeBackend();backend.busy=true;
            var screen=new NativeCustomFontScreen(null,backend);client.client().screen=screen;screen.width=300;screen.height=220;screen.init();
            var future=new CompletableFuture<NativeLocalFontService.Result>();set(screen,"pending",future);
            screen.onClose();assertEquals(1,backend.cancels);assertSame(screen,client.client().screen);
            assertNotNull(field(screen,"pending"));
            backend.busy=false;future.complete(NativeLocalFontService.Result.CANCELLED);screen.tick();assertNull(field(screen,"pending"));
            client.client().screen=null;screen.onClose();assertNull(client.client().screen);assertEquals(0,backend.closes);
            screen.removed();assertEquals(1,backend.closes);
        }
    }
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static final class FakeBackend implements NativeCustomFontScreen.Backend {
        int imports,cancels,closes;boolean busy;
        public CompletableFuture<NativeLocalFontService.Result> importConfirmed(Path path){imports++;return new CompletableFuture<>();}
        public CompletableFuture<NativeLocalFontService.Result> restore(){return new CompletableFuture<>();}
        public boolean busy(){return busy;}
        public void cancel(){cancels++;}
        public void close(){closes++;}
    }
}

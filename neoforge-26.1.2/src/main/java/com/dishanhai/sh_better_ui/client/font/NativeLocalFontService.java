// Author: dishanhai
package com.dishanhai.sh_better_ui.client.font;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One local import transaction; never navigates screens or uploads a player's font. */
public final class NativeLocalFontService implements AutoCloseable {
    public enum Result { APPLIED, RESTORED, CANCELLED, FAILED, RECOVERY_FAILED }
    interface Backend {
        Path prepare(Path file, AtomicBoolean cancelled) throws Exception;
        void execute(Runnable action);
        List<String> selected();
        void select(List<String> ids);
        boolean discover(String id);
        CompletableFuture<Void> reload();
        void persist();
    }
    private static final AtomicBoolean TRANSACTION=new AtomicBoolean();
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"shbui-local-font");t.setDaemon(true);return t;});
    private final Backend backend;
    private final Executor worker;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile boolean busy,applying,closed;
    NativeLocalFontService(Backend backend,Executor worker){this.backend=backend;this.worker=worker;}
    public static NativeLocalFontService create(Minecraft client) {
        return new NativeLocalFontService(new Backend(){
            public Path prepare(Path file,AtomicBoolean cancelled) throws Exception {
                var version=SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
                return NativeLocalFontPack.create(LocalFontPack.read(file),client.getResourcePackDirectory(),version.major(),version.minor(),cancelled::get);
            }
            public void execute(Runnable action){client.execute(action);}
            public List<String> selected(){return List.copyOf(client.getResourcePackRepository().getSelectedIds());}
            public void select(List<String> ids){client.getResourcePackRepository().setSelected(ids);}
            public boolean discover(String id){var repo=client.getResourcePackRepository();repo.reload();return repo.getPack(id)!=null;}
            public CompletableFuture<Void> reload(){return client.reloadResourcePacks();}
            public void persist(){
                client.options.resourcePacks.clear(); client.options.incompatibleResourcePacks.clear();
                for(var pack:client.getResourcePackRepository().getSelectedPacks()) if(!pack.isFixedPosition()) {
                    client.options.resourcePacks.add(pack.getId());
                    if(!pack.getCompatibility().isCompatible()) client.options.incompatibleResourcePacks.add(pack.getId());
                }
                client.options.save();
            }
        },WORKER);
    }
    public boolean busy(){return busy;}
    public boolean applying(){return applying;}
    public void cancel(){cancelled.set(true);}
    @Override public void close(){closed=true;cancel();}
    public CompletableFuture<Result> importConfirmed(Path file){return start(Objects.requireNonNull(file));}
    public CompletableFuture<Result> restore(){return start(null);}
    private CompletableFuture<Result> start(Path file){
        if(closed||busy||!TRANSACTION.compareAndSet(false,true)) return CompletableFuture.completedFuture(Result.FAILED);
        busy=true; cancelled.set(false);
        var result=new CompletableFuture<Result>();
        try {
        if(file==null) apply(null,result);
        else CompletableFuture.supplyAsync(()->{
            try{return backend.prepare(file,cancelled);}catch(Exception ex){throw new CompletionException(ex);}
        },worker).whenComplete((pack,error)->backend.execute(()->{
            if(cancelled.get()||closed){remove(pack);finish(result,Result.CANCELLED);}
            else if(error!=null) finish(result,Result.FAILED);
            else apply(pack,result);
        }));
        } catch(RuntimeException failure){finish(result,Result.FAILED);}
        return result;
    }
    private void apply(Path pack,CompletableFuture<Result> result){
        List<String> before;
        try {before=backend.selected();} catch(RuntimeException failure){remove(pack);finish(result,Result.FAILED);return;}
        if(cancelled.get()||closed){remove(pack);finish(result,Result.CANCELLED);return;}
        applying=true;
        try {
            var next=new ArrayList<>(before);
            next.removeIf(id->id.startsWith("file/"+LocalFontPack.PREFIX));
            if(pack!=null){String id="file/"+pack.getFileName();if(!backend.discover(id))throw new IllegalStateException("Font pack unavailable");next.add(id);}
            backend.select(next);
            backend.reload().whenComplete((unused,error)->backend.execute(()->{
                // Minecraft may recover internally and complete normally after removing broken packs.
                if(error!=null||cancelled.get()||closed||!backend.selected().equals(next)) rollback(before,pack,result);
                else {
                    try {backend.persist();finish(result,pack==null?Result.RESTORED:Result.APPLIED);}
                    catch(RuntimeException failure){rollback(before,pack,result);}
                }
            }));
        } catch(RuntimeException error){rollback(before,pack,result);}
    }
    private void rollback(List<String> before,Path pack,CompletableFuture<Result> result){
        try {
            backend.select(before);
            backend.reload().whenComplete((unused,error)->backend.execute(()->{
                boolean restored=error==null&&backend.selected().equals(before);
                if(restored)try{backend.persist();}catch(RuntimeException failure){restored=false;}
                remove(pack);
                finish(result,!restored?Result.RECOVERY_FAILED:cancelled.get()||closed?Result.CANCELLED:Result.FAILED);
            }));
        } catch(RuntimeException error){remove(pack);finish(result,Result.RECOVERY_FAILED);}
    }
    private static void remove(Path path){if(path!=null)try{Files.deleteIfExists(path);}catch(java.io.IOException ignored){/* An inert generated pack can be removed in Minecraft's pack folder. */}}
    private void finish(CompletableFuture<Result> result,Result value){busy=false;applying=false;TRANSACTION.set(false);result.complete(value);}
}

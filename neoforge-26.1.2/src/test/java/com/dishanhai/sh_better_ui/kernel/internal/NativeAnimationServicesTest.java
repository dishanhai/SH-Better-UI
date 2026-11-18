// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NativeAnimationServicesTest {
    static final ModuleDescriptor.ResourceClaim PROPERTY=new ModuleDescriptor.ResourceClaim(
            ModuleDescriptor.ResourceKind.ANIMATION_PROPERTY,UiId.parse("example:width"));
    @Test void discoveryPublishesBothServicesAndScopeCloseReleasesProperty()throws Exception {
        try(var fixture=new NativeAnimationFixture(Set.of(PROPERTY,CollapseService.PROGRESS_CLAIM))){
            var service=fixture.module.engine().require(UIApis.ANIMATION);
            assertSame(fixture.animation,service);assertSame(fixture.collapse,fixture.module.engine().require(UIApis.COLLAPSE));
            var values=new java.util.ArrayList<Double>();
            var track=service.bind(fixture.module,fixture.tree,PROPERTY.id(),Spring.PANEL,0,()->false,values::add);
            assertThrows(IllegalStateException.class,()->service.bind(fixture.module,fixture.tree,PROPERTY.id(),Spring.PANEL,0,()->false,v->{}));
            track.play(1,InterruptibleAnimation.Policy.RETARGET,0);fixture.frame(0);fixture.frame(80);
            assertTrue(track.value()>0 && track.value()<1);assertFalse(values.isEmpty());
            var fold=fixture.collapse.create(fixture.module,fixture.tree,new CollapseSpec());fold.expand();fixture.frame(200);
            assertTrue(fold.progress()>0);fixture.tree.close();assertFalse(track.isActive());
            assertThrows(IllegalStateException.class,fold::expand);
            var replacement=fixture.screens.mountTrusted(fixture.module,fixture.screen,UiId.parse("example:replacement"));
            var next=service.bind(fixture.module,replacement,PROPERTY.id(),Spring.PANEL,0,()->false,v->{});
            assertTrue(next.isActive());fixture.host.close();assertFalse(next.isActive());
        }
    }
    @Test void undeclaredPropertyAndForeignScopeCannotAcquireTracks()throws Exception {
        try(var fixture=new NativeAnimationFixture(Set.of())){
            assertThrows(SecurityException.class,()->fixture.animation.bind(fixture.module,fixture.tree,PROPERTY.id(),Spring.PANEL,0,()->false,v->{}));
            assertThrows(SecurityException.class,()->fixture.collapse.create(fixture.module,fixture.tree,new CollapseSpec()));
        }
        try(var fixture=new NativeAnimationFixture(Set.of(PROPERTY,CollapseService.PROGRESS_CLAIM))){
            var foreign=fixture.host.rootScope().openChild(ScopeKind.SCREEN,UiId.parse("sh_better_ui:foreign_screen"))
                    .openChild(ScopeKind.UI_TREE,UiId.parse("sh_better_ui:foreign_tree"));
            assertThrows(SecurityException.class,()->fixture.animation.bind(fixture.module,foreign,PROPERTY.id(),Spring.PANEL,0,()->false,v->{}));
            assertThrows(SecurityException.class,()->fixture.collapse.create(fixture.module,foreign,new CollapseSpec()));
            var legal=fixture.animation.bind(fixture.module,fixture.tree,PROPERTY.id(),Spring.PANEL,0,()->false,v->{});
            assertTrue(legal.isActive());
        }
    }
    @Test void pauseResumeKeepsValueThenClosingStopsCallbacks()throws Exception {
        try(var fixture=new NativeAnimationFixture(Set.of(PROPERTY))){
            var samples=new java.util.ArrayList<Double>();
            var track=fixture.animation.bind(fixture.module,fixture.tree,PROPERTY.id(),Spring.PANEL,0,()->false,samples::add);
            track.play(1,InterruptibleAnimation.Policy.RETARGET,0);fixture.frame(0);fixture.frame(40);
            track.pause();float value=track.value();int count=samples.size();fixture.frame(400);
            assertEquals(value,track.value());assertEquals(count,samples.size());
            track.resume();fixture.frame(480);assertTrue(track.value()>value);
            track.close();count=samples.size();fixture.frame(1000);assertEquals(count,samples.size());
            assertThrows(IllegalStateException.class,()->track.play(0,InterruptibleAnimation.Policy.RETARGET,0));
        }
    }
}

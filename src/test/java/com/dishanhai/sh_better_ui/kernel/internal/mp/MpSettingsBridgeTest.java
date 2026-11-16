package com.dishanhai.sh_better_ui.kernel.internal.mp;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MpSettingsBridgeTest {
    public static class Model {
        public static Entry[] entries;
        public static Category[] categories() {return new Category[]{new Category()};}
    }
    public static class Category { public String title="Category"; public Group[] groups={new Group()}; }
    public static class Group { public String title="Group"; public Entry[] entries=Model.entries; }
    public static class Setting {
        public float value=0.5f;
        public String getLabel(){return "Level";}
        public float getMin(){return 0;} public float getMax(){return 1;} public float getValue(){return value;}
        public void setValue(float value){this.value=value;}
    }
    public static class Entry {
        public MpSettingsBridge.Kind kind; public String description="Description"; public Setting setting=new Setting();
        public boolean locked, visible=true, on, fail; public String choice="A"; public Color color=Color.RED;
        Entry(MpSettingsBridge.Kind kind){this.kind=kind;}
        public boolean isVisible(){return visible;} public boolean isLocked(){return locked;}
        public String getLabel(){return kind.name();} public boolean isOn(){return on;}
        public String getValueText(){return choice;} public String[] getChoices(){return new String[]{"A","B"};}
        public String[] getChoiceHints(){return new String[]{"Local only","Sends packets to server; may trigger anticheat"};}
        public void setChoice(String v){choice=v;} public Color getColor(){return color;} public void setColor(Color v){color=v;}
        public void activate(){if(fail)throw new IllegalStateException("fixture failure");on=!on;}
    }
    private MpSettingsBridge bridge() {
        Model.entries=java.util.Arrays.stream(MpSettingsBridge.Kind.values()).map(Entry::new).toArray(Entry[]::new);
        return new MpSettingsBridge(getClass().getClassLoader(),Model.class.getName());
    }
    @Test void absentAndIncompatibleAreReported() {
        assertThrows(MpSettingsBridge.Failure.class,()->new MpSettingsBridge(getClass().getClassLoader(),"absent.mp.Model"));
        assertThrows(MpSettingsBridge.Failure.class,()->new MpSettingsBridge(getClass().getClassLoader(),String.class.getName()));
    }
    @Test void allKindsReadAndWriteLiveBackend() {
        var entries=bridge().categories().get(0).groups().get(0).entries(); assertEquals(6,entries.size());
        entries.get(0).value(.75f);assertEquals(.75f,entries.get(0).value());
        entries.get(1).activate();assertTrue(entries.get(1).on());
        entries.get(2).color(0xff123456);assertEquals(0xff123456,entries.get(2).color());
        entries.get(3).choice("B");assertEquals("B",entries.get(3).valueText());
        assertTrue(com.muoniumplayer.core.settings.HudConfig.saves>=4);
    }
    @Test void lockedOrHiddenEntriesCannotWriteAndInvalidValuesAreRejected() {
        var entries=bridge().categories().get(0).groups().get(0).entries();
        Model.entries[2].locked=true;assertThrows(IllegalStateException.class,()->entries.get(2).color(0));
        Model.entries[0].visible=false;assertThrows(IllegalStateException.class,()->entries.get(0).value(.25f));
        Model.entries[0].visible=true;assertThrows(IllegalArgumentException.class,()->entries.get(0).value(Float.NaN));
        assertThrows(IllegalArgumentException.class,()->entries.get(3).choice("unknown"));
    }
    @Test void reflectionUnwrapsFailureWithoutExecutingAgain() {
        var entries=bridge().categories().get(0).groups().get(0).entries();Model.entries[5].fail=true;
        var failure=assertThrows(MpSettingsBridge.Failure.class,()->entries.get(5).activate());
        assertInstanceOf(IllegalStateException.class,failure.getCause());assertFalse(Model.entries[5].on);
    }
}

// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativePagesModelTest {
 @Test void modSearchMatchesRealMetadataAndSortsDeterministically(){
  var a=new InstalledModData("z","Beta","2.0","description");var b=new InstalledModData("a","Alpha","1.0",null);
  assertEquals(List.of(b,a),InstalledModData.filter(List.of(a,b),""));
  assertEquals(List.of(a),InstalledModData.filter(List.of(a,b),"BETA"));
  assertEquals(List.of(b),InstalledModData.filter(List.of(a,b),"1.0"));
  assertEquals("",b.description());
 }
 @Test void searchUsesRootLocale(){
  var previous=Locale.getDefault();try{Locale.setDefault(Locale.forLanguageTag("tr"));assertEquals(1,InstalledModData.filter(List.of(new InstalledModData("id","ITEM","1","")),"item").size());}finally{Locale.setDefault(previous);}
 }
 @Test void displayPatchPreservesNonDisplaySettings(){
  var original=ClientUiSettings.DEFAULT;var edited=new DisplayPreferenceEdits(true,true,false).apply(original);
  assertSame(original.theme(),edited.theme());assertSame(original.feedback(),edited.feedback());assertSame(original.backdrop(),edited.backdrop());assertTrue(edited.highContrast());assertTrue(edited.reducedMotion());assertFalse(edited.widgetBackdropBlur());
 }
 @Test void untouchedFieldsMergeAndConcurrentChangedFieldConflicts(){
  var base=new DisplayPreferenceEdits(false,false,true);var edited=new DisplayPreferenceEdits(true,false,true);
  var current=new DisplayPreferenceEdits(false,true,false);
  assertFalse(edited.conflicts(base,current));assertEquals(new DisplayPreferenceEdits(true,true,false),edited.merge(base,current));
  assertTrue(edited.conflicts(base,new DisplayPreferenceEdits(true,false,true)));
 }
}

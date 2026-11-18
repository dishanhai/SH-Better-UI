// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class ConfirmationChoiceTest {
 @Test void confirmationIsConsumedBeforeReentrantCallbacks() {
  Object parent=new Object(), modal=new Object(); var current=new AtomicReference<>(modal); var calls=new AtomicInteger();
  var holder=new AtomicReference<ConfirmationChoice<Object>>();
  var choice=new ConfirmationChoice<>(parent,current::get,current::set,()->holder.get().finish(true),calls::incrementAndGet);
  holder.set(choice); choice.attach(modal); choice.finish(true); choice.finish(true);
  assertSame(parent,current.get()); assertEquals(1,calls.get());
 }
 @Test void focusLookupNavigationOrFailureNeverConfirms() {
  Object parent=new Object(), modal=new Object(), other=new Object(); var current=new AtomicReference<>(modal); var calls=new AtomicInteger();
  var choice=new ConfirmationChoice<>(parent,current::get,current::set,()->current.set(other),calls::incrementAndGet);
  choice.attach(modal); choice.finish(true); assertEquals(0,calls.get()); assertSame(other,current.get());
  current.set(modal);
  var failure=new ConfirmationChoice<>(parent,current::get,current::set,()->{throw new IllegalStateException("focus");},calls::incrementAndGet);
  failure.attach(modal); assertThrows(IllegalStateException.class,()->failure.finish(true));
  current.set(modal); failure.finish(true); assertEquals(0,calls.get());
 }
 @Test void cancellationAndAbandonmentNeverConfirm() {
  Object parent=new Object(), modal=new Object(); var current=new AtomicReference<>(modal); var calls=new AtomicInteger();
  var choice=new ConfirmationChoice<>(parent,current::get,current::set,()->{},calls::incrementAndGet);
  choice.attach(modal); choice.finish(false); assertSame(parent,current.get()); assertEquals(0,calls.get());
  current.set(modal); choice.finish(true); assertEquals(0,calls.get());
  var abandoned=new ConfirmationChoice<>(parent,current::get,current::set,()->{},calls::incrementAndGet);
  abandoned.attach(modal); abandoned.abandon(); abandoned.finish(true); assertSame(modal,current.get()); assertEquals(0,calls.get());
 }
 @Test void staleScreenAndParentRedirectDoNotConfirm() {
  Object parent=new Object(), modal=new Object(), other=new Object(); var current=new AtomicReference<>(other); var calls=new AtomicInteger();
  var choice=new ConfirmationChoice<>(parent,current::get,ignored->current.set(other),()->fail("must not restore"),calls::incrementAndGet);
  choice.attach(modal); choice.finish(true); assertSame(other,current.get());
  current.set(modal); choice.finish(true); assertSame(other,current.get()); assertEquals(0,calls.get());
 }
}

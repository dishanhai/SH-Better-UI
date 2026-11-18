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
 @Test void normalChoicesDoNotAbandonWhenReturningRemovesTheModal() {
  for(boolean accepted:new boolean[]{false,true}) {
   Object parent=new Object(),modal=new Object();var current=new AtomicReference<>(modal);
   var confirmed=new AtomicInteger();var abandoned=new AtomicInteger();var restored=new AtomicInteger();
   var holder=new AtomicReference<ConfirmationChoice<Object>>();
   var choice=new ConfirmationChoice<>(parent,current::get,next->{
    // Minecraft removes the current modal while showing its parent.
    holder.get().abandon();current.set(next);
   },restored::incrementAndGet,confirmed::incrementAndGet,abandoned::incrementAndGet);
   holder.set(choice);choice.attach(modal);choice.finish(accepted);choice.abandon();choice.finish(true);
   assertSame(parent,current.get());assertEquals(1,restored.get());
   assertEquals(accepted?1:0,confirmed.get());assertEquals(0,abandoned.get());
  }
 }
 @Test void externalReplacementAbandonsExactlyOnceAndCannotConfirmReentrantly() {
  Object parent=new Object(),modal=new Object(),other=new Object(),redirect=new Object();
  var current=new AtomicReference<>(modal);var abandoned=new AtomicInteger();var confirmed=new AtomicInteger();
  var holder=new AtomicReference<ConfirmationChoice<Object>>();
  var choice=new ConfirmationChoice<>(parent,current::get,current::set,()->fail("must not restore"),confirmed::incrementAndGet,()->{
   abandoned.incrementAndGet();holder.get().abandon();holder.get().finish(true);current.set(redirect);
  });
  holder.set(choice);choice.attach(modal);current.set(other);choice.abandon();choice.abandon();choice.finish(true);
  assertEquals(1,abandoned.get());assertEquals(0,confirmed.get());assertSame(redirect,current.get());
 }
 @Test void normalReturnThatNavigatesDuringFocusNeverConfirmsOrAbandonsTwice() {
  Object parent=new Object(),modal=new Object(),other=new Object();var current=new AtomicReference<>(modal);
  var confirmed=new AtomicInteger();var abandoned=new AtomicInteger();var holder=new AtomicReference<ConfirmationChoice<Object>>();
  var choice=new ConfirmationChoice<>(parent,current::get,current::set,()->{
   current.set(other);holder.get().abandon();holder.get().finish(true);
  },confirmed::incrementAndGet,abandoned::incrementAndGet);
  holder.set(choice);choice.attach(modal);choice.finish(true);choice.abandon();
  assertSame(other,current.get());assertEquals(0,confirmed.get());assertEquals(0,abandoned.get());
 }
 @Test void failedAbandonCleanupIsStillConsumedOnlyOnce() {
  Object parent=new Object(),modal=new Object();var current=new AtomicReference<>(modal);var abandoned=new AtomicInteger();
  var choice=new ConfirmationChoice<>(parent,current::get,current::set,()->fail("must not restore"),()->fail("must not confirm"),()->{
   abandoned.incrementAndGet();throw new IllegalStateException("cleanup");
  });
  choice.attach(modal);assertThrows(IllegalStateException.class,choice::abandon);
  assertDoesNotThrow(choice::abandon);choice.finish(true);assertEquals(1,abandoned.get());assertSame(modal,current.get());
 }
}

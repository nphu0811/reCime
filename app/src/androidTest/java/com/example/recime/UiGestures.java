package com.example.recime;

import android.view.InputDevice;
import android.view.MotionEvent;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.action.*;
import java.util.Arrays;

/** Real touchscreen events queued as one sequence; never call View.performClick or retry. */
public final class UiGestures {
  private UiGestures() {}
  public static ViewAction click() {
    return new GeneralClickAction(new Tapper() {
      @Override public Status sendTap(UiController controller,float[] point,float[] precision) {
        return sendTap(controller,point,precision,InputDevice.SOURCE_TOUCHSCREEN,0);
      }
      @Override public Status sendTap(UiController controller,float[] point,float[] precision,int source,int buttons) {
        MotionEvent down=MotionEvents.obtainDownEvent(point,precision,source,buttons);
        MotionEvent up=MotionEvent.obtain(down.getDownTime(),down.getEventTime()+10,
            MotionEvent.ACTION_UP,point[0],point[1],0);
        up.setSource(down.getSource());
        try {
          // Espresso 3.7's separate sendDown waits on the UI thread before sending UP.
          // Its own oversleep detection can report a long press on a slow emulator.
          // The sequence API queues DOWN asynchronously and synchronizes after UP.
          if(!controller.injectMotionEventSequence(Arrays.asList(down,up)))
            throw new AssertionError("Touch sequence injection failed; no retry");
          return Status.SUCCESS;
        } catch (androidx.test.espresso.InjectEventSecurityException failure) {
          throw new AssertionError("Touch sequence injection denied",failure);
        } finally { down.recycle();up.recycle(); }
      }
    },GeneralLocation.VISIBLE_CENTER,Press.FINGER,InputDevice.SOURCE_TOUCHSCREEN,0);
  }
}

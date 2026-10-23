package com.example.recime.helper;
import android.view.*;

/** Keep active drafts and cook-along out of root-tab replacement gestures. */
public final class ModalChrome {
  private final View dock, host;
  private final int visibility, margin;
  public ModalChrome(android.app.Activity activity) {
    dock=activity.findViewById(com.example.recime.R.id.bottom);host=activity.findViewById(com.example.recime.R.id.host);
    visibility=dock.getVisibility();margin=((ViewGroup.MarginLayoutParams)host.getLayoutParams()).bottomMargin;
    dock.setVisibility(View.GONE); ViewGroup.MarginLayoutParams params=(ViewGroup.MarginLayoutParams)host.getLayoutParams();params.bottomMargin=0;host.setLayoutParams(params);
  }
  public void restore() {
    dock.setVisibility(visibility);ViewGroup.MarginLayoutParams params=(ViewGroup.MarginLayoutParams)host.getLayoutParams();params.bottomMargin=margin;host.setLayoutParams(params);
  }
}

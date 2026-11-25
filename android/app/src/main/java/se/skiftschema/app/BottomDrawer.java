package se.skiftschema.app;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;

// Created programmatically with the sheet content and dismissal action.
@SuppressLint("ViewConstructor")
final class BottomDrawer extends FrameLayout {
    private final View sheet;
    private final ScrollView scroll;
    private final Runnable dismiss;
    private final int touchSlop;
    private final int dismissDistance;
    private float downX;
    private float downY;
    private boolean canDrag;
    private boolean dragging;
    private boolean dismissing;

    BottomDrawer(Ui ui, View sheet, ScrollView scroll, Runnable dismiss) {
        super(ui.activity);
        this.sheet = sheet;
        this.scroll = scroll;
        this.dismiss = dismiss;
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        dismissDistance = ui.dp(72);
        addView(sheet, new LayoutParams(Ui.MATCH, Ui.WRAP));
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (dismissing) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                sheet.animate().cancel();
                sheet.setTranslationY(0);
                downX = event.getX();
                downY = event.getY();
                boolean onHandle = downY < scroll.getTop();
                canDrag = onHandle || (!scroll.canScrollVertically(-1) && !hitsPicker(this, downX, downY));
            }
            case MotionEvent.ACTION_MOVE -> {
                float dy = event.getY() - downY;
                float dx = Math.abs(event.getX() - downX);
                if (canDrag && dy > touchSlop && dy > dx) {
                    dragging = true;
                    sheet.setTranslationY(dy);
                    return true;
                }
                if (dx > touchSlop || dy < -touchSlop) canDrag = false;
            }
            case MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> canDrag = false;
            default -> { }
        }
        return false;
    }

    // The sheet's children handle clicks and Android Back dismisses the sheet.
    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (dismissing || !dragging) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE -> sheet.setTranslationY(Math.max(0, event.getY() - downY));
            case MotionEvent.ACTION_UP -> {
                float distance = event.getY() - downY;
                finishDrag(distance >= Math.min(dismissDistance, sheet.getHeight() / 3f));
            }
            case MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> finishDrag(false);
            default -> { }
        }
        return true;
    }

    private void finishDrag(boolean close) {
        dragging = false;
        canDrag = false;
        dismissing = close;
        var animation = sheet.animate().translationY(close ? getHeight() : 0).setDuration(160);
        if (close) animation.withEndAction(() -> {
            if (isAttachedToWindow()) dismiss.run();
        });
        animation.start();
    }

    private boolean hitsPicker(View view, float x, float y) {
        if (view instanceof NumberPicker) return true;
        if (!(view instanceof ViewGroup group)) return false;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            float childX = x + group.getScrollX() - child.getX();
            float childY = y + group.getScrollY() - child.getY();
            if (child.getVisibility() == VISIBLE && childX >= 0 && childX < child.getWidth()
                    && childY >= 0 && childY < child.getHeight() && hitsPicker(child, childX, childY)) {
                return true;
            }
        }
        return false;
    }
}

package ru.artemzozula.mobileindicator;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

final class SignalView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = Ui.BLUE;
    private int iconId;
    private Drawable icon;
    private float progress = 1;
    private ValueAnimator animator;

    SignalView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void show(int resource, int accent) {
        if (resource == iconId) return;
        iconId = resource;
        color = accent;
        icon = getContext().getDrawable(resource).mutate();
        icon.setTint(color);
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.setDuration(540);
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        float x = getWidth() * .5f, y = getHeight() * .5f;
        float r = Math.min(x, y);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setAlpha(24);
        canvas.drawCircle(x, y, r * .55f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 1));
        for (int i = 0; i < 3; i++) {
            paint.setAlpha((int) ((65 - i * 17) * progress));
            canvas.drawCircle(x, y, r * (.58f + i * .18f) * (.88f + progress * .12f), paint);
        }
        if (icon != null) {
            int half = Math.round(r * .27f);
            icon.setBounds((int) x - half, (int) y - half, (int) x + half, (int) y + half);
            icon.setAlpha((int) (140 + progress * 115));
            icon.draw(canvas);
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}

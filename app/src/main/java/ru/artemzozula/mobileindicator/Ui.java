package ru.artemzozula.mobileindicator;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class Ui {
    static final int BACK = 0xFF0B1019, SURFACE = 0xFF141D2A, LINE = 0xFF293749;
    static final int INK = 0xFFF1F6FF, MUTED = 0xFFA8B8CD, BLUE = 0xFFA5CDFF;
    static final int GREEN = 0xFF6DE4BC, AMBER = 0xFFFFCA78, RED = 0xFFFFA2AB;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static LinearLayout column(Context c) {
        LinearLayout view = new LinearLayout(c);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    static LinearLayout screen(Activity activity) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(BACK);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets b = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(b.left, b.top, b.right, b.bottom);
                return insets;
            });
        }
        LinearLayout page = column(activity);
        pad(page, 20, 22);
        scroll.addView(page);
        activity.setContentView(scroll);
        return page;
    }

    static void pad(android.view.View v, int x, int y) {
        int h = dp(v.getContext(), x), w = dp(v.getContext(), y);
        v.setPadding(h, w, h, w);
    }

    static GradientDrawable shape(Context c, int fill, int border, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(c, radius));
        if (border != 0) background.setStroke(dp(c, 1), border);
        return background;
    }

    static TextView text(Context c, String title, int sp, int color, boolean strong) {
        TextView view = new TextView(c);
        view.setText(title);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", strong ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    static android.widget.Button button(Context c, String title, boolean primary, Runnable action) {
        android.widget.Button view = new android.widget.Button(c);
        view.setText(title);
        view.setAllCaps(false);
        view.setTextSize(14);
        view.setTextColor(primary ? BACK : INK);
        view.setMinHeight(dp(c, 48));
        view.setGravity(Gravity.CENTER);
        pad(view, 12, 8);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337A9CC2),
                shape(c, primary ? BLUE : SURFACE, primary ? 0 : LINE, 16), null));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    static void add(LinearLayout parent, android.view.View view, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(parent.getContext(), top);
        parent.addView(view, lp);
    }
}

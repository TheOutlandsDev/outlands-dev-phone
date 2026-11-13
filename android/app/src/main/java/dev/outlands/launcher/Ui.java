package dev.outlands.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

final class Ui {
    static final int BACKGROUND = 0xff080b11;
    static final int PANEL = 0xff0d0e11;
    static final int BORDER = 0xff36363b;
    static final int YELLOW = 0xfff4ef27;
    static final int CYAN = 0xff62e8f5;
    static final int WHITE = 0xfff3f6fa;
    static final int MUTED = 0xffb9c9d5;
    static final int ERROR = 0xffffa49c;

    private Ui() { }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }

    static TextView eyebrow(Context context, String value) {
        TextView view = text(context, value, 10, MUTED, false);
        view.setTypeface(Typeface.MONOSPACE);
        view.setLetterSpacing(0.12f);
        return view;
    }

    static GradientDrawable panel(Context context, int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(0);
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    static RippleDrawable clickable(Context context, boolean accent) {
        return new RippleDrawable(ColorStateList.valueOf(accent ? 0x24408080 : 0x22f4ef27),
                panel(context, accent ? YELLOW : PANEL, accent ? YELLOW : BORDER), null);
    }

    static Button button(Context context, String label, boolean accent) {
        Button view = new Button(context);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(14);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(accent ? BACKGROUND : WHITE);
        view.setMinHeight(dp(context, 48));
        view.setMinimumHeight(dp(context, 48));
        view.setMinWidth(0);
        view.setMinimumWidth(0);
        view.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        view.setBackground(clickable(context, accent));
        return view;
    }

    static EditText input(Context context, String hint) {
        EditText view = new EditText(context);
        view.setHint(hint);
        view.setSingleLine(true);
        view.setTextSize(15);
        view.setTextColor(WHITE);
        view.setHintTextColor(MUTED);
        view.setMinHeight(dp(context, 56));
        view.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        view.setBackground(panel(context, PANEL, BORDER));
        return view;
    }

    static void edgeToEdge(Window window, View root, int horizontalPadding) {
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets keyboard = windowInsets.getInsets(WindowInsets.Type.ime());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = Math.max(bars.bottom, keyboard.bottom);
            } else {
                left = windowInsets.getSystemWindowInsetLeft();
                top = windowInsets.getSystemWindowInsetTop();
                right = windowInsets.getSystemWindowInsetRight();
                bottom = windowInsets.getSystemWindowInsetBottom();
            }
            view.setPadding(left + horizontalPadding, top, right + horizontalPadding, bottom);
            return windowInsets;
        });
        root.requestApplyInsets();
    }
}

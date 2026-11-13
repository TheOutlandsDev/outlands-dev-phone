package dev.outlands.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.Locale;

/** Original geometric marks and native icon framing. Does not alter another app's icon or APK. */
final class OutlandsBadge extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String kind;
    private final Drawable appIcon;

    OutlandsBadge(Context context, String kind, Drawable appIcon) {
        super(context);
        this.kind = kind;
        this.appIcon = appIcon;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    static String kindFor(String value) {
        String text = value.toLowerCase(Locale.ROOT);
        if (text.contains("term") || text.contains("shell") || text.contains("python") || text.contains("node")) return "terminal";
        if (text.contains("tail") || text.contains("network") || text.contains("vpn")) return "network";
        if (text.contains("git") || text.contains("repository")) return "git";
        if (text.contains("grafana") || text.contains("loki") || text.contains("metric") || text.contains("observ") || text.contains("monitor")) return "chart";
        if (text.contains("chat") || text.contains("webui") || text.contains("workbench") || text.contains(" ai") || text.startsWith("ai")) return "ai";
        if (text.contains("browser") || text.contains("chrome") || text.contains("web")) return "browser";
        if (text.contains("setting") || text.contains("toolkit") || text.contains("tools")) return "settings";
        if (text.contains("code") || text.contains("develop") || text.contains("rust") || text.contains("go ")) return "code";
        return "hub";
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 100f, getHeight() / 100f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff151820);
        canvas.drawRect(1, 1, 99, 99, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Ui.BORDER);
        canvas.drawRect(1, 1, 99, 99, paint);
        if (appIcon != null) {
            appIcon.setBounds(20, 20, 80, 80);
            appIcon.draw(canvas);
        } else {
            paint.setColor(Ui.CYAN);
            paint.setStrokeWidth(5);
            paint.setStrokeCap(Paint.Cap.SQUARE);
            drawGlyph(canvas);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.YELLOW);
        Path corner = new Path();
        corner.moveTo(80, 0); corner.lineTo(100, 0); corner.lineTo(100, 20); corner.close();
        canvas.drawPath(corner, paint);
        paint.setColor(Ui.CYAN);
        canvas.drawRect(0, 79, 3, 100, paint);
        canvas.drawRect(0, 97, 21, 100, paint);
        canvas.restore();
    }

    private void drawGlyph(Canvas canvas) {
        if ("terminal".equals(kind)) {
            line(canvas, 26, 32, 43, 48); line(canvas, 43, 48, 26, 64); line(canvas, 51, 66, 73, 66);
        } else if ("network".equals(kind)) {
            line(canvas, 50, 29, 27, 66); line(canvas, 50, 29, 73, 66); line(canvas, 27, 66, 73, 66);
            node(canvas, 50, 27); node(canvas, 25, 69); node(canvas, 75, 69);
        } else if ("code".equals(kind)) {
            line(canvas, 34, 31, 19, 50); line(canvas, 19, 50, 34, 69);
            line(canvas, 66, 31, 81, 50); line(canvas, 81, 50, 66, 69); line(canvas, 57, 26, 44, 74);
        } else if ("ai".equals(kind)) {
            Path star = new Path();
            star.moveTo(50, 20); star.lineTo(59, 41); star.lineTo(80, 50); star.lineTo(59, 59);
            star.lineTo(50, 80); star.lineTo(41, 59); star.lineTo(20, 50); star.lineTo(41, 41); star.close();
            canvas.drawPath(star, paint);
        } else if ("chart".equals(kind)) {
            line(canvas, 22, 24, 22, 76); line(canvas, 22, 76, 80, 76);
            line(canvas, 37, 65, 37, 51); line(canvas, 54, 65, 54, 39); line(canvas, 71, 65, 71, 24);
        } else if ("git".equals(kind)) {
            line(canvas, 35, 29, 35, 72); line(canvas, 35, 55, 66, 37); node(canvas, 35, 26); node(canvas, 35, 75); node(canvas, 69, 34);
        } else if ("browser".equals(kind)) {
            canvas.drawCircle(50, 50, 29, paint);
            canvas.drawOval(new RectF(38, 21, 62, 79), paint);
            line(canvas, 22, 50, 78, 50); line(canvas, 29, 34, 71, 34); line(canvas, 29, 66, 71, 66);
        } else if ("settings".equals(kind)) {
            canvas.drawCircle(50, 50, 20, paint); canvas.drawCircle(50, 50, 6, paint);
            for (int i = 0; i < 8; i++) {
                canvas.save(); canvas.rotate(i * 45, 50, 50); line(canvas, 50, 18, 50, 29); canvas.restore();
            }
        } else {
            canvas.drawRect(24, 24, 43, 43, paint); canvas.drawRect(57, 24, 76, 43, paint);
            canvas.drawRect(24, 57, 43, 76, paint); canvas.drawRect(57, 57, 76, 76, paint);
        }
    }

    private void line(Canvas canvas, float x1, float y1, float x2, float y2) { canvas.drawLine(x1, y1, x2, y2, paint); }
    private void node(Canvas canvas, float x, float y) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(Ui.YELLOW); canvas.drawCircle(x, y, 6, paint);
        paint.setColor(Ui.CYAN); paint.setStyle(Paint.Style.STROKE);
    }
}

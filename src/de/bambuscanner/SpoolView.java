package de.bambuscanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Zeichnet eine Filamentspule von oben (ähnlich den AMS-Slots in Bambu Studio). */
public final class SpoolView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int c1 = 0xFF808080;
    private Integer c2 = null;
    private int alpha = 255;
    private boolean empty = true;

    public SpoolView(Context ctx) {
        super(ctx);
    }

    public void setColors(int color, Integer color2, int alpha) {
        this.c1 = color;
        this.c2 = color2;
        this.alpha = Math.max(alpha, 60);
        this.empty = false;
        invalidate();
    }

    public void setEmpty() {
        empty = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f, R = size / 2f;

        // Flansch
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF45454C);
        c.drawCircle(cx, cy, R, p);

        float rf = R * 0.90f;
        if (empty) {
            p.setColor(0xFF2B2B30);
            c.drawCircle(cx, cy, rf, p);
        } else {
            rect.set(cx - rf, cy - rf, cx + rf, cy + rf);
            if (c2 != null) {
                p.setColor(withAlpha(c1));
                c.drawArc(rect, 90, 180, true, p);
                p.setColor(withAlpha(c2));
                c.drawArc(rect, -90, 180, true, p);
            } else {
                p.setColor(withAlpha(c1));
                c.drawCircle(cx, cy, rf, p);
            }
            // angedeutete Wicklungen
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, R * 0.012f));
            p.setColor(isLight(c1) ? 0x22000000 : 0x22FFFFFF);
            for (float r = R * 0.50f; r < rf; r += R * 0.07f) c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.FILL);
        }

        // Nabe
        p.setColor(0xFF5A5A62);
        c.drawCircle(cx, cy, R * 0.42f, p);
        p.setColor(0xFF3A3A41);
        c.drawCircle(cx, cy, R * 0.34f, p);
        p.setColor(0xFF1F1F23);
        c.drawCircle(cx, cy, R * 0.17f, p);
    }

    private int withAlpha(int col) {
        return Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col));
    }

    static boolean isLight(int col) {
        return (0.299 * Color.red(col) + 0.587 * Color.green(col) + 0.114 * Color.blue(col)) > 160;
    }
}

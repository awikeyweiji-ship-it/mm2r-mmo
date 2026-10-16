package com.jeyu.clashgptstarter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Purely decorative brand mark: the state is reflected through labels elsewhere. */
public final class TechOrbitView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int signal = 0; // 0 = disconnected; 1 = system VPN present; 2 = HTTPS verified

    public TechOrbitView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setSignal(int value) {
        int next = Math.max(0, Math.min(2, value));
        if (signal != next) { signal = next; invalidate(); }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * .38f;
        int accent = signal == 2 ? UiPalette.READY : signal == 1 ? UiPalette.ACCENT : UiPalette.BLUE;
        p.setShader(new RadialGradient(cx, cy, radius * 1.5f,
                new int[]{0x383A89C7, 0x15244F8A, Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius * 1.5f, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(.9f));
        p.setColor(0x4968ADD5);
        canvas.drawCircle(cx, cy, radius * 1.08f, p);
        p.setColor(0x334EA5CE);
        canvas.drawCircle(cx, cy, radius * 1.22f, p);

        RectF outer = new RectF(cx - radius * 1.22f, cy - radius * 1.22f,
                cx + radius * 1.22f, cy + radius * 1.22f);
        p.setStrokeWidth(dp(2.2f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(accent);
        canvas.drawArc(outer, -102, 61, false, p);
        p.setColor(0xaa5F99EA);
        canvas.drawArc(outer, 26, 38, false, p);
        p.setColor(0x9062BFE2);
        canvas.drawArc(outer, 146, 43, false, p);

        RectF inner = new RectF(cx-radius*.91f,cy-radius*.91f,cx+radius*.91f,cy+radius*.91f);
        p.setColor(0x6860B4DB);
        p.setStrokeWidth(dp(1.3f));
        p.setPathEffect(new DashPathEffect(new float[]{dp(2), dp(8)}, 0));
        canvas.drawArc(inner, 0, 360, false, p);
        p.setPathEffect(null);

        p.setShader(new LinearGradient(cx-radius, cy-radius, cx+radius, cy+radius,
                new int[]{0xff1B4263, 0xff0D2341}, null, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius * .70f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0x886DD2F4);
        p.setStrokeWidth(dp(1.4f));
        canvas.drawCircle(cx, cy, radius*.70f, p);

        // Distinctive minimal monogram, not a ChatGPT trademark or impersonated official logo.
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(UiPalette.TEXT);
        p.setTextSize(dp(37));
        canvas.drawText("G", cx, cy + dp(11), p);
        p.setTextSize(dp(10));
        p.setColor(accent);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        canvas.drawText("GATEWAY", cx, cy + radius*.49f, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(accent);
        canvas.drawCircle(cx + radius*1.05f, cy - radius*.52f, dp(3.3f), p);
        p.setColor(0x6066E9E6);
        canvas.drawCircle(cx + radius*1.05f, cy - radius*.52f, dp(8.3f), p);
    }

    private float dp(float d) { return d * getResources().getDisplayMetrics().density; }
}

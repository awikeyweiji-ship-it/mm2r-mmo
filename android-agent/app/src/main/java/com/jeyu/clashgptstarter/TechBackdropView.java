package com.jeyu.clashgptstarter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Decorative, low-power static tech atmosphere; zero bitmap downloads or sensor access. */
public final class TechBackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TechBackdropView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{UiPalette.BG, 0xff0B1830, 0xff08101E}, null,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * .56f, h * .20f,
                Math.max(w, h) * .47f,
                new int[]{0x382F7BB7, 0x16204C91, Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * .10f, h * .82f,
                Math.max(w, h) * .40f,
                new int[]{0x193B6EB0, 0x09304B99, Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        float grid = dp(34);
        paint.setColor(0x102C7099);
        paint.setStrokeWidth(dp(.6f));
        paint.setStyle(Paint.Style.STROKE);
        for (float x = grid * .5f; x < w; x += grid) canvas.drawLine(x, 0, x, h, paint);
        for (float y = 0; y < h; y += grid) canvas.drawLine(0, y, w, y, paint);

        // Reproducible stars instead of random visual changes between redraws.
        for (int i = 0; i < 37; i++) {
            float x = (float)((i * 81 + 43) % 301) / 301f * w;
            float y = (float)((i * 127 + 19) % 317) / 317f * h;
            paint.setColor(i % 4 == 0 ? 0x6663E2F8 : 0x294A83BB);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, y, dp(i % 5 == 0 ? 1.3f : .7f), paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x25389EC2);
        paint.setStrokeWidth(dp(1));
        canvas.drawLine(w * .82f, 0, w * .25f, h * .42f, paint);
        paint.setColor(0x162AC6DD);
        canvas.drawLine(w * .91f, 0, w * .41f, h * .38f, paint);
    }

    private float dp(float d) { return d * getResources().getDisplayMetrics().density; }
}

package com.pepperlfuchs.fairdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

public class MascotView extends View {
    private final Bitmap mascot;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint browPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float targetX = 0f, targetY = 0f, targetSize = 0.18f;
    private float currentX = 0f, currentY = 0f, currentSize = 0.18f;
    private long lastFaceMs = 0L;
    private boolean cameraReady = true;

    public MascotView(Context context) {
        super(context);
        mascot = BitmapFactory.decodeResource(getResources(), R.drawable.mascot_base);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        eyePaint.setColor(0xFF0BC6BE);
        pupilPaint.setColor(0xFF071011);
        whitePaint.setColor(0xFFFFFFFF);
        browPaint.setColor(0xFF0BC6BE);
        browPaint.setStrokeCap(Paint.Cap.ROUND);
        browPaint.setStrokeWidth(8f);

        textPaint.setColor(0xFF44484A);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(30f);
    }

    public void trackFace(float x, float y, float size) {
        targetX = x;
        targetY = y;
        targetSize = size;
        lastFaceMs = SystemClock.uptimeMillis();
    }

    public void noFace() {
        if (SystemClock.uptimeMillis() - lastFaceMs > 450L) {
            targetX = 0f;
            targetY = 0f;
            targetSize = 0.18f;
        }
    }

    public void setCameraReady(boolean ready) {
        cameraReady = ready;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || mascot == null) return;

        LinearGradient bg = new LinearGradient(0, 0, 0, h,
                0xFFFFFFFF, 0xFFF1F3F2, Shader.TileMode.CLAMP);
        paint.setShader(bg);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        currentX += (targetX - currentX) * 0.11f;
        currentY += (targetY - currentY) * 0.11f;
        currentSize += (targetSize - currentSize) * 0.08f;

        float bw = mascot.getWidth();
        float bh = mascot.getHeight();
        float baseScale = Math.min((w * 0.96f) / bw, (h * 0.96f) / bh);
        float distanceBoost = 1f + clamp((currentSize - 0.18f) * 0.22f, -0.025f, 0.045f);
        float s = baseScale * distanceBoost;

        float bodyShiftX = currentX * bw * 0.040f;
        float bodyShiftY = currentY * bh * 0.018f;
        float bodyRotation = currentX * 3.2f;

        canvas.save();
        canvas.translate(w / 2f, h / 2f);
        canvas.translate(bodyShiftX * s, bodyShiftY * s);
        canvas.rotate(bodyRotation);
        canvas.scale(s, s);
        canvas.translate(-bw / 2f, -bh / 2f);

        canvas.drawBitmap(mascot, 0f, 0f, paint);
        drawAnimatedEyes(canvas);
        canvas.restore();

        if (!cameraReady) {
            textPaint.setTextSize(Math.max(24f, w * 0.026f));
            canvas.drawText("Kamera izni gerekli", w / 2f, h * 0.94f, textPaint);
        }

        postInvalidateOnAnimation();
    }

    private void drawAnimatedEyes(Canvas canvas) {
        float leftX = 385f;
        float rightX = 535f;
        float eyeY = 307f;
        float radiusX = 53f;
        float radiusY = 58f;

        float eyeDx = currentX * 22f;
        float eyeDy = currentY * 14f;

        long now = SystemClock.uptimeMillis();
        long cycle = now % 3900L;
        float blink = 1f;
        if (cycle < 160L) {
            float t = cycle / 160f;
            blink = Math.abs(1f - 2f * t);
            blink = Math.max(0.08f, blink);
        }
        radiusY *= blink;

        drawEye(canvas, leftX, eyeY, radiusX, radiusY, eyeDx, eyeDy);
        drawEye(canvas, rightX, eyeY, radiusX, radiusY, eyeDx, eyeDy);

        if (blink > 0.35f) {
            canvas.drawLine(leftX - 34f, eyeY - 76f, leftX + 31f, eyeY - 83f - currentY * 5f, browPaint);
            canvas.drawLine(rightX - 31f, eyeY - 83f - currentY * 5f, rightX + 34f, eyeY - 76f, browPaint);
        }
    }

    private void drawEye(Canvas canvas, float cx, float cy, float rx, float ry, float dx, float dy) {
        RectF outer = new RectF(cx - rx, cy - ry, cx + rx, cy + ry);
        canvas.drawOval(outer, eyePaint);

        float pupilRx = rx * 0.48f;
        float pupilRy = Math.max(2f, ry * 0.55f);
        float px = cx + dx;
        float py = cy + dy;
        RectF pupil = new RectF(px - pupilRx, py - pupilRy, px + pupilRx, py + pupilRy);
        canvas.drawOval(pupil, pupilPaint);

        float glintR = Math.max(3f, Math.min(rx, ry) * 0.14f);
        canvas.drawCircle(px + pupilRx * 0.38f, py - pupilRy * 0.42f, glintR, whitePaint);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

package com.pepperlfuchs.fairdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

public class RobotView extends View {
    private final Bitmap robot;
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float targetX = 0f, targetY = 0f, targetSize = 0.2f;
    private float x = 0f, y = 0f, faceSize = 0.2f;
    private float targetYaw = 0f, targetRoll = 0f, yaw = 0f, roll = 0f;
    private boolean faceVisible = false;
    private boolean frontCamera = true;
    private boolean cameraError = false;
    private long lastFrame = SystemClock.elapsedRealtime();
    private long nextBlink = lastFrame + 2200;
    private long blinkStart = -1L;
    private float blink = 0f;

    public RobotView(Context context, Bitmap robot) {
        super(context);
        this.robot = robot;
        setBackgroundColor(Color.rgb(247, 249, 248));
    }

    public void setFrontCamera(boolean front) { frontCamera = front; }
    public void setCameraError(boolean error) { cameraError = error; invalidate(); }

    public void trackFace(float nx, float ny, float size, float headYaw, float headRoll) {
        targetX = clamp(frontCamera ? -nx : nx, -1f, 1f);
        targetY = clamp(ny, -1f, 1f);
        targetSize = clamp(size, 0.08f, 0.75f);
        targetYaw = clamp(headYaw / 35f, -1f, 1f);
        targetRoll = clamp(headRoll / 25f, -1f, 1f);
        faceVisible = true;
        postInvalidateOnAnimation();
    }

    public void faceLost() {
        faceVisible = false;
        targetX = 0f;
        targetY = 0f;
        targetYaw = 0f;
        targetRoll = 0f;
        targetSize = 0.2f;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.elapsedRealtime();
        float dt = Math.min(0.05f, Math.max(0.001f, (now - lastFrame) / 1000f));
        lastFrame = now;
        float smooth = 1f - (float)Math.exp(-dt * 7.5f);
        x += (targetX - x) * smooth;
        y += (targetY - y) * smooth;
        yaw += (targetYaw - yaw) * smooth;
        roll += (targetRoll - roll) * smooth;
        faceSize += (targetSize - faceSize) * smooth;
        updateBlink(now);

        float vw = getWidth(), vh = getHeight();
        float srcRatio = robot.getWidth() / (float)robot.getHeight();
        float maxW = vw * 0.90f;
        float maxH = vh * 0.95f;
        float drawW = Math.min(maxW, maxH * srcRatio);
        float drawH = drawW / srcRatio;
        float baseLeft = (vw - drawW) / 2f;
        float baseTop = (vh - drawH) / 2f + vh * 0.012f;

        float bodyShiftX = x * vw * 0.055f;
        float bodyShiftY = y * vh * 0.018f;
        float bodyRotate = -x * 5.2f - roll * 1.3f;
        float scaleBoost = 1f + (faceSize - 0.2f) * 0.06f;

        RectF dst = new RectF(baseLeft, baseTop, baseLeft + drawW, baseTop + drawH);
        canvas.save();
        canvas.translate(bodyShiftX, bodyShiftY);
        canvas.rotate(bodyRotate, vw / 2f, baseTop + drawH * 0.63f);
        canvas.scale(scaleBoost, scaleBoost, vw / 2f, baseTop + drawH * 0.63f);
        canvas.drawBitmap(robot, null, dst, imagePaint);

        float vx1 = baseLeft + drawW * 0.305f;
        float vy1 = baseTop + drawH * 0.182f;
        float vx2 = baseLeft + drawW * 0.747f;
        float vy2 = baseTop + drawH * 0.366f;
        RectF visor = new RectF(vx1, vy1, vx2, vy2);
        shape.setColor(Color.rgb(3, 10, 12));
        canvas.drawRoundRect(visor, drawW * 0.055f, drawW * 0.055f, shape);

        float eyeY = baseTop + drawH * 0.277f;
        float eyeLX = baseLeft + drawW * 0.425f;
        float eyeRX = baseLeft + drawW * 0.625f;
        float eyeR = drawW * 0.055f;
        float lookX = x * eyeR * 0.62f + yaw * eyeR * 0.12f;
        float lookY = y * eyeR * 0.42f;
        drawEye(canvas, eyeLX, eyeY, eyeR, lookX, lookY, blink);
        drawEye(canvas, eyeRX, eyeY, eyeR, lookX, lookY, blink);
        canvas.restore();

        float dotX = vw * 0.955f, dotY = vh * 0.035f;
        shape.setColor(cameraError ? Color.rgb(210, 48, 48) : (faceVisible ? Color.rgb(0, 167, 142) : Color.rgb(170, 180, 177)));
        canvas.drawCircle(dotX, dotY, Math.max(5f, vw * 0.006f), shape);

        postInvalidateOnAnimation();
    }

    private void drawEye(Canvas c, float cx, float cy, float r, float dx, float dy, float blinkAmount) {
        float open = Math.max(0.06f, 1f - blinkAmount);
        shape.setColor(Color.rgb(0, 209, 190));
        c.drawOval(new RectF(cx-r, cy-r*open, cx+r, cy+r*open), shape);
        shape.setColor(Color.rgb(3, 12, 14));
        float pr = r * 0.43f;
        c.drawCircle(cx + dx, cy + dy, pr * Math.max(0.30f, open), shape);
        shape.setColor(Color.WHITE);
        c.drawCircle(cx + dx + pr*0.34f, cy + dy - pr*0.30f, pr*0.19f*Math.max(0.35f, open), shape);
    }

    private void updateBlink(long now) {
        if (blinkStart < 0 && now >= nextBlink) blinkStart = now;
        if (blinkStart >= 0) {
            long elapsed = now - blinkStart;
            if (elapsed >= 190) {
                blink = 0f;
                blinkStart = -1L;
                nextBlink = now + 2200 + (long)(Math.random() * 2800);
            } else {
                blink = (float)Math.sin(Math.PI * elapsed / 190.0);
            }
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

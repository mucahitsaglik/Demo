package com.pepperlfuchs.fairdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final int REQ_CAMERA = 42;
    private RobotView robotView;
    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private volatile boolean processing = false;
    private long lastFaceAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        byte[] bytes = android.util.Base64.decode(RobotAsset.JPG_BASE64, android.util.Base64.DEFAULT);
        Bitmap robot = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        robotView = new RobotView(this, robot);
        setContentView(robotView);

        cameraExecutor = Executors.newSingleThreadExecutor();
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .setMinFaceSize(0.12f)
                .build();
        faceDetector = FaceDetection.getClient(options);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                CameraSelector selector = CameraSelector.DEFAULT_FRONT_CAMERA;
                try {
                    provider.unbindAll();
                    provider.bindToLifecycle(this, selector, analysis);
                    robotView.setFrontCamera(true);
                } catch (Exception frontError) {
                    provider.unbindAll();
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis);
                    robotView.setFrontCamera(false);
                }
            } catch (Exception e) {
                robotView.setCameraError(true);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(@NonNull ImageProxy proxy) {
        if (processing || proxy.getImage() == null) {
            proxy.close();
            return;
        }
        processing = true;
        int rotation = proxy.getImageInfo().getRotationDegrees();
        InputImage input = InputImage.fromMediaImage(proxy.getImage(), rotation);

        faceDetector.process(input)
                .addOnSuccessListener(faces -> onFaces(faces, proxy.getWidth(), proxy.getHeight(), rotation))
                .addOnFailureListener(e -> { })
                .addOnCompleteListener(task -> {
                    processing = false;
                    proxy.close();
                });
    }

    private void onFaces(List<Face> faces, int rawW, int rawH, int rotation) {
        if (faces == null || faces.isEmpty()) {
            if (SystemClock.elapsedRealtime() - lastFaceAt > 900) robotView.faceLost();
            return;
        }

        Face best = faces.get(0);
        float bestArea = 0f;
        for (Face f : faces) {
            float a = f.getBoundingBox().width() * f.getBoundingBox().height();
            if (a > bestArea) { best = f; bestArea = a; }
        }

        int iw = (rotation == 90 || rotation == 270) ? rawH : rawW;
        int ih = (rotation == 90 || rotation == 270) ? rawW : rawH;
        float cx = best.getBoundingBox().exactCenterX();
        float cy = best.getBoundingBox().exactCenterY();
        float nx = ((cx / Math.max(1f, iw)) - 0.5f) * 2f;
        float ny = ((cy / Math.max(1f, ih)) - 0.5f) * 2f;
        float size = best.getBoundingBox().width() / Math.max(1f, iw);

        lastFaceAt = SystemClock.elapsedRealtime();
        robotView.trackFace(nx, ny, size, best.getHeadEulerAngleY(), best.getHeadEulerAngleZ());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            robotView.setCameraError(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceDetector != null) faceDetector.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}

package com.pepperlfuchs.fairdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
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
    private MascotView mascotView;
    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    mascotView.setCameraReady(true);
                    startCamera();
                } else {
                    mascotView.setCameraReady(false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterKioskLook();

        mascotView = new MascotView(this);
        setContentView(mascotView);

        cameraExecutor = Executors.newSingleThreadExecutor();
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.12f)
                .enableTracking()
                .build();
        faceDetector = FaceDetection.getClient(options);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            mascotView.setCameraReady(true);
            startCamera();
        } else {
            mascotView.setCameraReady(false);
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void enterKioskLook() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setStatusBarColor(0xFFF7F7F5);
        window.setNavigationBarColor(0xFFF7F7F5);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
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

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis);
            } catch (Exception e) {
                mascotView.post(() -> mascotView.setCameraReady(false));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        InputImage image = InputImage.fromMediaImage(mediaImage, rotation);
        int frameW = (rotation == 90 || rotation == 270) ? imageProxy.getHeight() : imageProxy.getWidth();
        int frameH = (rotation == 90 || rotation == 270) ? imageProxy.getWidth() : imageProxy.getHeight();

        faceDetector.process(image)
                .addOnSuccessListener(faces -> handleFaces(faces, frameW, frameH))
                .addOnFailureListener(e -> mascotView.post(mascotView::noFace))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void handleFaces(List<Face> faces, int frameWpx, int frameHpx) {
        if (faces == null || faces.isEmpty()) {
            mascotView.post(mascotView::noFace);
            return;
        }

        Face best = null;
        int bestArea = -1;
        for (Face face : faces) {
            Rect r = face.getBoundingBox();
            int area = Math.max(1, r.width()) * Math.max(1, r.height());
            if (area > bestArea) {
                bestArea = area;
                best = face;
            }
        }
        if (best == null) return;

        Rect r = best.getBoundingBox();
        float frameW = Math.max(1f, frameWpx);
        float frameH = Math.max(1f, frameHpx);

        float nx = ((r.centerX() / frameW) - 0.5f) * 2f;
        float ny = ((r.centerY() / frameH) - 0.5f) * 2f;

        nx = -nx;
        nx = clamp(nx, -1f, 1f);
        ny = clamp(ny, -1f, 1f);

        float size = (float) Math.sqrt((r.width() * r.height()) / (frameW * frameH));
        float finalNx = nx;
        float finalNy = ny;
        float finalSize = clamp(size, 0.05f, 0.55f);
        mascotView.post(() -> mascotView.trackFace(finalNx, finalNy, finalSize));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterKioskLook();
    }

    @Override
    protected void onDestroy() {
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (faceDetector != null) faceDetector.close();
        super.onDestroy();
    }
}

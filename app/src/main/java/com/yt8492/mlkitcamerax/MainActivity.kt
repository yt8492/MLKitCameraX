package com.yt8492.mlkitcamerax

import android.Manifest
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Size
import android.view.Surface
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import kotlinx.android.synthetic.main.activity_main.*
import permissions.dispatcher.*
import java.util.concurrent.Executors

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val labeler = FirebaseVision.getInstance().onDeviceImageLabeler
    private val captureListener = object : ImageCapture.OnImageCapturedListener() {
        private fun degreesToFirebaseRotation(degrees: Int): Int = when(degrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw Exception("Rotation must be 0, 90, 180, or 270.")
        }

        override fun onCaptureSuccess(image: ImageProxy?, rotationDegrees: Int) {
            val mediaImage = image?.image
            val imageRotation = degreesToFirebaseRotation(rotationDegrees)
            if (mediaImage != null) {
                val firebaseVisionImage = FirebaseVisionImage.fromMediaImage(mediaImage, imageRotation)
                labeler.processImage(firebaseVisionImage)
                    .addOnSuccessListener { labels ->
                        labels.firstOrNull()?.let {
                            labelView.text = "${it.text}: ${it.confidence}"
                        }
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                    }
            }
        }

        override fun onError(
            imageCaptureError: ImageCapture.ImageCaptureError,
            message: String,
            cause: Throwable?
        ) {
            cause?.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startCameraWithPermissionCheck()
    }

    @NeedsPermission(Manifest.permission.CAMERA)
    fun startCamera() {
        cameraView.post {
            setupCamera()
        }
        cameraView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
    }

    private fun setupCamera() {
        val previewConfig = PreviewConfig.Builder()
            .setTargetResolution(Size(cameraView.width, cameraView.height))
            .build()
        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener {
            val parent = cameraView.parent as ViewGroup
            parent.removeView(cameraView)
            parent.addView(cameraView, 0)
            cameraView.surfaceTexture = it.surfaceTexture
            updateTransform()
        }
        val captureConfig = ImageCaptureConfig.Builder()
            .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            .build()
        val capture = ImageCapture(captureConfig)
        captureView.setOnClickListener {
            capture.takePicture(executor, captureListener)
        }
        CameraX.bindToLifecycle(this, preview, capture)
    }

    private fun updateTransform() {
        val matrix = Matrix()
        val centerX = cameraView.width / 2f
        val centerY = cameraView.height / 2f
        val rotationDegrees = when (cameraView.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        cameraView.setTransform(matrix)
    }

    @OnShowRationale(Manifest.permission.CAMERA)
    fun showRationaleForCamera(request: PermissionRequest) {
        AlertDialog.Builder(this)
            .setMessage("カメラへのアクセスを許可しますか？")
            .setPositiveButton("はい") { _, _ -> request.proceed() }
            .setNegativeButton("いいえ") { _, _ -> request.cancel() }
            .setCancelable(false)
            .show()
    }

    @OnPermissionDenied(Manifest.permission.CAMERA)
    fun onCameraDenied() {
        Toast.makeText(this, "アクセスが許可されませんでした", Toast.LENGTH_SHORT).show()
    }

    @OnNeverAskAgain(Manifest.permission.CAMERA)
    fun onCameraNeverAskAgain() {
        Toast.makeText(this, "今後表示しないが選択されました", Toast.LENGTH_SHORT).show()
    }
}

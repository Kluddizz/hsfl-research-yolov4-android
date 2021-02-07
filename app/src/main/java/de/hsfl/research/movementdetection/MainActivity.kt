package de.hsfl.research.movementdetection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.renderscript.RenderScript
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import de.hsfl.research.movementdetection.detection.Box
import de.hsfl.research.movementdetection.detection.BoxDrawer
import de.hsfl.research.movementdetection.detection.YoloDetector
import de.hsfl.research.movementdetection.detection.YoloPostProcessor
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

// Implementation based on https://androidpedia.net/en/tutorial/619/camera-2-api
class MainActivity : AppCompatActivity(), CameraXConfig.Provider {

  companion object {
    const val REQUEST_CAMERA_PERMISSION = 10
    const val MAX_PREVIEW_WIDTH = 1920
    const val MAX_PREVIEW_HEIGHT = 1080
  }

  private lateinit var mDetector: YoloDetector
  private lateinit var mCameraProviderFuture: ListenableFuture<ProcessCameraProvider>
  private lateinit var mPreviewView: PreviewView
  private lateinit var mImageView: ImageView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    mPreviewView = findViewById(R.id.previewView)
    mImageView = findViewById(R.id.imageView)

    mDetector = YoloDetector(assets, "yolov4-tiny.bin", "yolov4-tiny.param")
    mDetector.postProcessor = YoloPostProcessor(this, mImageView)

    mCameraProviderFuture = ProcessCameraProvider.getInstance(this)
    mCameraProviderFuture.addListener(Runnable {
      val cameraProvider = mCameraProviderFuture.get()
      bindPreview(cameraProvider)
    }, ContextCompat.getMainExecutor(this))
  }

  private fun bindPreview(cameraProvider: ProcessCameraProvider) {
    val preview: Preview = Preview.Builder()
      .build()

    val cameraSelector: CameraSelector = CameraSelector.Builder()
      .requireLensFacing(CameraSelector.LENS_FACING_BACK)
      .build()

    val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
      .setTargetResolution(Size(1280, 720))
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .build()

    val executor = ContextCompat.getMainExecutor(this)
    imageAnalysis.setAnalyzer(executor, mDetector)

    preview.setSurfaceProvider(mPreviewView.surfaceProvider)
    cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageAnalysis, preview)
  }

  private fun startCamera(width: Int, height: Int) {
    // If the app has no permission to use the hardware camera, ask for it.
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      requestCameraPermission()
      return
    }
  }

  private fun requestCameraPermission() {
    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
      AlertDialog.Builder(this)
        .setMessage("R string request permission")
        .setPositiveButton(R.string.ok) { _, _-> ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION) }
        .setNegativeButton(R.string.cancel) { _, _ -> finish() }
        .create()
    } else {
      ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    when (requestCode) {
      REQUEST_CAMERA_PERMISSION ->
        if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
          Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
      else ->
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
  }

  override fun getCameraXConfig(): CameraXConfig {
    return Camera2Config.defaultConfig()
  }

}
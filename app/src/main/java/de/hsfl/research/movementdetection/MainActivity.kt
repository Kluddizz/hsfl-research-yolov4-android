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
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import de.hsfl.research.movementdetection.detection.YoloDetector
import de.hsfl.research.movementdetection.detection.YoloPostProcessor
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

// Implementation based on https://androidpedia.net/en/tutorial/619/camera-2-api
class MainActivity : AppCompatActivity() {

  companion object {
    const val REQUEST_CAMERA_PERMISSION = 10
    const val MAX_PREVIEW_WIDTH = 1920
    const val MAX_PREVIEW_HEIGHT = 1080
  }

  private var mTextureView: TextureView? = null
  private var mCameraDevice: CameraDevice? = null
  private var mCaptureSession: CameraCaptureSession? = null
  private var mPreviewSize: Size? = null
  private var mCaptureRequestBuilder: CaptureRequest.Builder? = null
  private var mCaptureRequest: CaptureRequest? = null
  private var mCameraOpenCloseLock: Semaphore = Semaphore(1)
  private var mCameraId: String = ""
  private var mBackgroundHandler: Handler? = null
  private var mImageReader: ImageReader? = null
  private var mBackgroundThread: HandlerThread? = null
  private lateinit var mDetector: YoloDetector

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    mTextureView = findViewById(R.id.textureView)
    mDetector = YoloDetector(assets, "yolov4-tiny.bin", "yolov4-tiny.param")
    mDetector.postProcessor = YoloPostProcessor()
  }

  // This listener listens to TextureView changes.
  private val mSurfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
      openCamera(width, height)
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
      configureTransform(width, height)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
      return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {

    }
  }

  // This callback instance handles the states of the camera object.
  private val mStateCallback = object : CameraDevice.StateCallback() {
    override fun onOpened(camera: CameraDevice) {
      mCameraOpenCloseLock.release()
      mCameraDevice = camera
      createCameraPreviewSession()
    }

    override fun onDisconnected(camera: CameraDevice) {
      mCameraOpenCloseLock.release()
      camera.close()
      mCameraDevice = null
    }

    override fun onError(camera: CameraDevice, error: Int) {
      mCameraOpenCloseLock.release()
      camera.close()
      mCameraDevice = null
      finish()
    }
  }

  private fun openCamera(width: Int, height: Int) {
    // If the app has no permission to use the hardware camera, ask for it.
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      requestCameraPermission()
      return
    }

    setUpCameraOutputs(width, height)
    configureTransform(width, height)
    val cameraManager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

    try {
      if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw RuntimeException("Time out waiting to lock camera opening.")
      }

      // Try to open the camera and initialize it.
      cameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler)
    } catch (e: CameraAccessException) {
      e.printStackTrace()
    } catch (e: InterruptedException) {
      throw RuntimeException("Interrupted while trying to lock camera opening.", e)
    }
  }

  private fun closeCamera() {
    mCameraOpenCloseLock.acquire()

    mCaptureSession?.close()
    mCaptureSession = null

    mCameraDevice?.close()
    mCameraDevice = null

    mImageReader?.close()
    mImageReader = null

    mCameraOpenCloseLock.release()
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

  private fun setUpCameraOutputs(width: Int, height: Int) {
    val cameraManager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

    for (cameraId: String in cameraManager.cameraIdList) {
      val characteristics = cameraManager.getCameraCharacteristics(cameraId)
      val facing = characteristics.get(CameraCharacteristics.LENS_FACING);

      // Make sure we don't use the front camera.
      if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
        continue

      val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        ?: continue

      val list = map.getOutputSizes(ImageFormat.JPEG).toList()
      val largest: Size = Collections.max(list, CompareSizesByArea())
      mImageReader = ImageReader.newInstance(largest.width, largest.height, ImageFormat.JPEG, 2)
      mImageReader?.setOnImageAvailableListener(mDetector, mBackgroundHandler)

      val displaySize = Point()
      windowManager.defaultDisplay.getSize(displaySize)
      var maxPreviewWidth = displaySize.x
      var maxPreviewHeight = displaySize.y

      if (maxPreviewWidth > MAX_PREVIEW_WIDTH)
        maxPreviewWidth = MAX_PREVIEW_WIDTH

      if (maxPreviewHeight > MAX_PREVIEW_HEIGHT)
        maxPreviewHeight = MAX_PREVIEW_HEIGHT

      mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), width, height, maxPreviewWidth, maxPreviewHeight, largest)
      mCameraId = cameraId
    }
  }

  private fun configureTransform(width: Int, height: Int) {
    if (mTextureView == null || mPreviewSize == null) {
      return
    }

    val rotation = windowManager.defaultDisplay.rotation
    val matrix = Matrix()
    val viewRect = RectF(0.0f, 0.0f, height.toFloat(), width.toFloat())
    val bufferRect = RectF(0.0f, 0.0f, mPreviewSize?.height!!.toFloat(), mPreviewSize?.width!!.toFloat())
    val centerX = viewRect.centerX()
    val centerY = viewRect.centerY()

    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)

      val scale = Math.max(
        height.toFloat() / mPreviewSize!!.height,
        width.toFloat() / mPreviewSize!!.width
      )

      matrix.postScale(scale, scale, centerX, centerY)
      matrix.postRotate(90.0f * (rotation - 2), centerX, centerY)
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180.0f, centerX, centerY)
    }

    mTextureView?.setTransform(matrix)
  }

  private fun createCameraPreviewSession() {
    val texture = mTextureView?.surfaceTexture
    texture?.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
    val surface = Surface(texture)

    mCaptureRequestBuilder = mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    mCaptureRequestBuilder?.addTarget(surface)
    mCaptureRequestBuilder?.addTarget(mImageReader?.surface!!)

    mCameraDevice?.createCaptureSession(listOf(surface, mImageReader?.surface), object : CameraCaptureSession.StateCallback() {
      override fun onConfigureFailed(session: CameraCaptureSession) {
        Toast.makeText(applicationContext, "Could not configure capture session.", Toast.LENGTH_SHORT).show()
      }

      override fun onConfigured(session: CameraCaptureSession) {
        if (mCameraDevice == null)
          return

        mCaptureSession = session
        mCaptureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        mCaptureRequest = mCaptureRequestBuilder?.build()
        mCaptureSession?.setRepeatingRequest(mCaptureRequest!!, null, mBackgroundHandler)
      }

    }, null)
  }

  private fun chooseOptimalSize(choises: Array<Size>, textureViewWidth: Int, textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size) : Size {
    val bigEnough = ArrayList<Size>()
    val notBigEnough = ArrayList<Size>()

    val w = aspectRatio.width
    val h = aspectRatio.height

    for (size: Size in choises) {
      if (size.width <= maxWidth && size.height <= maxHeight && size.height == size.width * h / w) {
        if (size.width >= textureViewWidth && size.height >= textureViewHeight) {
          bigEnough.add(size)
        } else {
          notBigEnough.add(size)
        }
      }
    }

    if (bigEnough.size > 0) {
      return Collections.min(bigEnough, CompareSizesByArea())
    } else if (notBigEnough.size > 0) {
      return Collections.max(notBigEnough, CompareSizesByArea())
    } else {
      Log.e("MainActivity", "Could not find any suitable preview size")
      return choises[0]
    }
  }

  private fun startBackgroundThread() {
    mBackgroundThread = HandlerThread("CameraBackground")
    mBackgroundThread?.start()
    mBackgroundHandler = Handler(mBackgroundThread!!.looper)
  }

  private fun stopBackgroundThread() {
    mBackgroundThread?.quitSafely()

    try {
      mBackgroundThread?.join()
      mBackgroundThread = null
      mBackgroundHandler = null
    } catch (e: InterruptedException) {
      e.printStackTrace()
    }
  }

  override fun onResume() {
    super.onResume()
    startBackgroundThread();

    if (mTextureView!!.isAvailable) {
      openCamera(mTextureView!!.width, mTextureView!!.height)
    } else {
      mTextureView!!.surfaceTextureListener = mSurfaceTextureListener
    }
  }

  override fun onPause() {
    closeCamera()
    stopBackgroundThread()
    super.onPause()
  }

}
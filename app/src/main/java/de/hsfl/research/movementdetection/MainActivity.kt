package de.hsfl.research.movementdetection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

class MainActivity : AppCompatActivity() {

  private val RESPONSE_CODE_CAMERA_PERMISSION = 10
  private var mCamera: CameraDevice? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    checkPermissions()
  }

  external fun stringFromJNI(): String

  private fun checkPermissions() {
    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(arrayOf(Manifest.permission.CAMERA), RESPONSE_CODE_CAMERA_PERMISSION)
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    when (requestCode) {
      RESPONSE_CODE_CAMERA_PERMISSION -> {
        if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
          initCamera()
        }
      }
    }
  }

  private fun initCamera() {
    val manager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // This callback will be used to initialize some fields later.
    val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
      override fun onOpened(camera: CameraDevice) {
        mCamera = camera
        Log.i("MainActivity", "Camera has been initialized.")
      }

      override fun onDisconnected(camera: CameraDevice) {
        mCamera = null
      }

      override fun onError(camera: CameraDevice, error: Int) {
      }
    }

    for (id in manager.cameraIdList) {
      val characteristics: CameraCharacteristics = manager.getCameraCharacteristics(id)

      if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
        try {
          // Open the camera and set some fields, so we have control over it.
          manager.openCamera(id, stateCallback, null)
        } catch (e: SecurityException) {
          // Maybe there are some permissions missing.
        } finally {
          break
        }
      }
    }
  }

  companion object {
    // Used to load the 'native-lib' library on application startup.
    init {
      System.loadLibrary("native-lib")
    }
  }
}
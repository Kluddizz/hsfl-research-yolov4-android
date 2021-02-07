package de.hsfl.research.movementdetection.detection

import android.content.res.AssetManager
import android.graphics.*
import android.media.ImageReader
import android.util.Log
import android.view.TextureView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

class YoloDetector(
        assetManager: AssetManager,
        detectorWeightsFile: String,
        detectorConfigurationFile: String
) : ImageAnalysis.Analyzer {

  var postProcessor: PostProcessor? = null
  private var mDetecting = false

  init {
    init(assetManager, detectorWeightsFile, detectorConfigurationFile)
  }

  external fun init(mgr: AssetManager, binFile: String, paramFile: String) : Boolean
  external fun detect(image: Bitmap) : Array<Box>

  companion object {
    val TAG: String = YoloDetector::class.java.simpleName

    init {
      System.loadLibrary("ncnn-yolov4")
    }
  }

  interface PostProcessor {
    fun postProcess(image: Bitmap, boundingBoxes: Array<Box>)
  }

  private fun convertImageToBitmap(image: ImageProxy) : Bitmap {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    uBuffer.get(nv21, ySize, vSize)
    vBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
    val imageBytes = out.toByteArray()

    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
  }

  override fun analyze(image: ImageProxy) {
    val bitmap = convertImageToBitmap(image)
    image.close()

    if (!mDetecting) {
      Thread {
        mDetecting = true
        val boxes = detect(bitmap)
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        postProcessor?.postProcess(mutableBitmap, boxes)
        mDetecting = false
      }.start()
    }
  }

}
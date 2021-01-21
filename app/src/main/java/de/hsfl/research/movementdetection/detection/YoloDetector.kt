package de.hsfl.research.movementdetection.detection

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ImageReader

class YoloDetector(
        assetManager: AssetManager,
        detectorWeightsFile: String,
        detectorConfigurationFile: String
) : ImageReader.OnImageAvailableListener {

  var postProcessor: PostProcessor? = null

  init {
    init(assetManager, detectorWeightsFile, detectorConfigurationFile)
  }

  private fun getBitmap(reader: ImageReader) : Bitmap {
    val image = reader.acquireLatestImage()

    val buffer = image.planes[0].buffer
    buffer.rewind()

    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    image.close()

    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  }

  override fun onImageAvailable(reader: ImageReader?) {
    if (reader == null)
      return

    // Read the current frame and convert bytes to bitmap.
    val bitmap = getBitmap(reader)

    // Execute the neural network and receive all detected objects as bounding boxes.
    val boxes = detect(bitmap)

    // If there is a post processor assigned, execute it.
    postProcessor?.postProcess(bitmap, boxes)
  }

  external fun init(mgr: AssetManager, binFile: String, paramFile: String) : Boolean
  external fun detect(image: Bitmap) : Array<Box>

  companion object {
    init {
      System.loadLibrary("ncnn-yolov4")
    }
  }

  interface PostProcessor {
    fun postProcess(image: Bitmap, boundingBoxes: Array<Box>)
  }
}
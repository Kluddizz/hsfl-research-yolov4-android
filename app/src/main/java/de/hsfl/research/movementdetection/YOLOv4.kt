package de.hsfl.research.movementdetection

import android.content.res.AssetManager
import android.graphics.Bitmap

class YOLOv4 {
  external fun init(mgr: AssetManager) : Boolean
  external fun detect(image: Bitmap) : Array<Box>

  companion object {
    init {
      System.loadLibrary("darknet")
    }
  }
}
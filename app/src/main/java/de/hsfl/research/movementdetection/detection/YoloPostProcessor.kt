package de.hsfl.research.movementdetection.detection

import android.graphics.Bitmap
import android.util.Log

class YoloPostProcessor : YoloDetector.PostProcessor {

    override fun postProcess(image: Bitmap, boundingBoxes: Array<Box>) {
        Log.i("YoloPostProcessor", boundingBoxes.size.toString())
    }

}
package de.hsfl.research.movementdetection.detection

import android.app.Activity
import android.graphics.Bitmap
import android.widget.ImageView

class YoloPostProcessor(activity: Activity, imageView: ImageView) : YoloDetector.PostProcessor {

    private var mBoxDrawer: BoxDrawer = BoxDrawer(activity, imageView)

    override fun postProcess(image: Bitmap, boundingBoxes: Array<Box>) {
        mBoxDrawer.drawCenters(image, boundingBoxes)
    }

}
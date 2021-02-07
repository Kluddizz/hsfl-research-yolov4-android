package de.hsfl.research.movementdetection.detection

import android.app.Activity
import android.graphics.*
import android.widget.ImageView

class BoxDrawer(private val activity: Activity, private val imageView: ImageView) {

    fun drawBoxes(mutableBitmap: Bitmap, boxes: Array<Box>) {
        val canvas = Canvas(mutableBitmap)
        val paint = Paint()

        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4 * mutableBitmap.width / 800.0f

        for (box in boxes) {
            canvas.drawRect(RectF(box.x1, box.y1, box.x2, box.y2), paint)
        }

        activity.runOnUiThread {
            imageView.setImageBitmap(mutableBitmap)
        }
    }

    fun drawCenters(mutableBitmap: Bitmap, boxes: Array<Box>) {
        val canvas = Canvas(mutableBitmap)
        val paint = Paint()
        val cursorThickness = 2.0f
        val cursorSize = 15.0f

        paint.color = Color.RED
        paint.style = Paint.Style.FILL

        for (box in boxes) {
            val center = box.center
            canvas.drawRect(RectF(center.x - cursorThickness / 2, center.y - cursorSize / 2, center.x + cursorThickness / 2, center.y + cursorSize / 2), paint)
            canvas.drawRect(RectF(center.x - cursorSize / 2, center.y - cursorThickness / 2, center.x + cursorSize / 2, center.y + cursorThickness / 2), paint)
        }

        activity.runOnUiThread {
            imageView.setImageBitmap(mutableBitmap)
        }
    }

}
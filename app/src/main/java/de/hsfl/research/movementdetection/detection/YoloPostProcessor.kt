package de.hsfl.research.movementdetection.detection

import android.graphics.Bitmap
import android.util.Log

class YoloPostProcessor : YoloDetector.PostProcessor {

    override fun postProcess(image: Bitmap, boundingBoxes: Array<Box>) {
        Log.i("YoloPostProcessor", boundingBoxes.size.toString())

        TODO("Jan-Erik: Hier kannst du mit deiner Bewegungserkennung starten." +
                "Du bekommst hier die BoundingBoxes (Rechtecke), die jeweils durch zwei" +
                "Punkte definiert werden. Jede Box hat übrigens eine Eigenschaft 'center'," +
                "die den Mittelpunkt dessen liefert.")
    }

}
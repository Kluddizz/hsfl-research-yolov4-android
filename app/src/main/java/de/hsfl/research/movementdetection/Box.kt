package de.hsfl.research.movementdetection

class Box(
  x1: Float,
  y1: Float,
  x2: Float,
  y2: Float,
  score: Float,
  label: Int
) {
  var x1 : Float = x1
    private set

  var y1 : Float = y1
    private set

  var x2 : Float = x2
    private set

  var y2 : Float = y2
    private set

  var score: Float = score
    private set

  var label: Int = label
    private set
}
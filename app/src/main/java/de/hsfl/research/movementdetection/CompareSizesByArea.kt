package de.hsfl.research.movementdetection

import android.util.Size

internal class CompareSizesByArea : Comparator<Size> {

  override fun compare(lhs: Size, rhs: Size): Int {
    return java.lang.Long.signum(lhs.width.toLong() * lhs.height -
      rhs.width.toLong() * rhs.height)
  }

}
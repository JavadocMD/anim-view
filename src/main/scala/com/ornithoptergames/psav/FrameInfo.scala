package com.ornithoptergames.psav

import javafx.scene.image.WritableImage

case class Size(val w: Double, val h: Double)

case class FrameInfo(val size: Size, val frames: List[Frame]) {
  override lazy val toString = "FrameInfo(%s, %d frames)".format(size, frames.size)
}

case class Frame(val image: WritableImage, val name: String,
    val top: Double, val bottom: Double,
    val left: Double, val right: Double) {

  val width = right - left
  val height = bottom - top

  override def toString() = "Frame(L: %.0f, R: %.0f, T: %.0f, B: %.0f)".format(left, right, top, bottom)
}
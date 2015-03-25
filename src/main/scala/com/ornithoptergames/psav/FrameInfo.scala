package com.ornithoptergames.psav

import scala.xml.Node

import javafx.scene.image.WritableImage

case class Size(val w: Double, val h: Double)

case class FrameInfo(val size: Size, val frames: List[Frame]) {
  override lazy val toString = "FrameInfo(%s, %d frames)".format(size, frames.size)
}

object Frame {
  def apply(node: Node, image: WritableImage): Frame = Frame(
    (node \ "@name").text,
    image,
    (node \ "@top").text.toDouble,
    (node \ "@bottom").text.toDouble,
    (node \ "@left").text.toDouble,
    (node \ "@right").text.toDouble)

  def apply(t: (Node, WritableImage)): Frame = t match { case (node, image) => Frame(node, image) }
}

case class Frame(val name: String, val image: WritableImage,
    val top: Double, val bottom: Double,
    val left: Double, val right: Double) {

  val width = right - left
  val height = bottom - top

  override def toString() = "Frame(L: %.0f, R: %.0f, T: %.0f, B: %.0f)".format(left, right, top, bottom)
}
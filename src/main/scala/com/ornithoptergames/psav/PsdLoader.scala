package com.ornithoptergames.psav

import scala.xml.parsing.NoBindingFactoryAdapter
import com.twelvemonkeys.imageio.plugins.psd.PSDImageReader
import javax.xml.transform.sax.SAXResult
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.ImageIO
import javax.xml.transform.dom.DOMSource
import javafx.embed.swing.SwingFXUtils
import scala.xml.Node
import java.io.File
import javafx.scene.image.WritableImage

object PsdLoader {
  
  def asXml(dom: org.w3c.dom.Node): Node = {
    val source = new DOMSource(dom)
    val adapter = new NoBindingFactoryAdapter
    val saxResult = new SAXResult(adapter)
    val transformerFactory = javax.xml.transform.TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.transform(source, saxResult)
    adapter.rootElem
  }

  lazy val psdReader: Option[PSDImageReader] = {
    val rs = ImageIO.getImageReadersByFormatName("psd")

    if (!rs.hasNext()) None
    else Some(rs.next().asInstanceOf[PSDImageReader])
  }

  def loadPsd(file: File): Option[FrameInfo] = {
    for (r <- psdReader; src <- Option(ImageIO.createImageInputStream(file))) yield {
      try {
        r.setInput(src)
        
        val is = (1 until r.getNumImages(true)) // layer 0 is the flattened image, I think, so skip it
        val images = is.map(i => SwingFXUtils.toFXImage(r.read(i), null))

        val meta = r.getImageMetadata(0)
        val root = asXml(meta.getAsTree("com_twelvemonkeys_imageio_psd_image_1.0").asInstanceOf[IIOMetadataNode])
        val frames = (root \ "Layers" \ "LayerInfo" zip images).map { Frame(_) }

        val width = (frames map { _.right }).max
        val height = (frames map { _.bottom }).max
        
        FrameInfo(Size(width, height), frames.toList)
      } finally {
        src.close()
      }
    }
  }
}



case class Size(val w: Double, val h: Double)

case class FrameInfo(val size: Size, val frames: List[Frame]) {
  override lazy val toString = "FrameInfo(%s, %d frames)".format(size, frames.size)
}

case class Frame(val name: String, val image: WritableImage,
  val top: Double, val bottom: Double,
  val left: Double, val right: Double) {
  
  val width = right - left
  val height = bottom - top
  
  override def toString() = "Frame(L: %.0f, R: %.0f, T: %.0f, B: %.0f)".format(left, right, top, bottom)
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
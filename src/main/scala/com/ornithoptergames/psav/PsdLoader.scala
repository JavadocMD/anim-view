package com.ornithoptergames.psav

import java.io.File

import scala.util.Failure
import scala.util.Try
import scala.xml.Node
import scala.xml.parsing.NoBindingFactoryAdapter

import com.ornithoptergames.psav.FrameInfoLoader._
import com.twelvemonkeys.imageio.plugins.psd.PSDImageReader
import com.twelvemonkeys.imageio.plugins.psd.PSDMetadata

import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.WritableImage
import javax.imageio.ImageIO
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.sax.SAXResult

object PsdLoader extends FileLoader {

  override def load(file: File): Try[FrameInfo] = delegate.load(file)
  
  /* We know  when the program starts whether or not we have a reader for a given format.
   * Instead of having to pepper in checks for that each time, possibly doing a lot of pointless
   * work in the process, just store a delegate which knows whether the reader exists or not.
   */
  private lazy val delegate: FileLoader = {
    val rs = ImageIO.getImageReadersByFormatName("psd")
    if (rs.hasNext()) new Delegate(rs.next().asInstanceOf[PSDImageReader])
    else FailedDelegate
  }
  
  private object FailedDelegate extends FileLoader {
    val failure = Failure(new LoadException("ImageIO reader not found."))
    override def load(file: File): Try[FrameInfo] = failure
  }
  
  private class Delegate(reader: PSDImageReader) extends FileLoader {
    
    override def load(file: File): Try[FrameInfo] = {
      for {
        (images, xml) <- readImage(reader, file)
      } yield {
          val frames = (xml \ "Layers" \ "LayerInfo" zip images).map { Frame(_) }
          val width = (frames map { _.right }).max
          val height = (frames map { _.bottom }).max
          FrameInfo(Size(width, height), frames.toList)
      }
    }
  }
  
  private[this] def readImage(reader: PSDImageReader, file: File): Try[(Seq[WritableImage], Node)] = {
    
    // Let's stick all this ugly stateful, exception-throwing code in here...
    def unsafe(reader: PSDImageReader, file: File) = {
      val src = ImageIO.createImageInputStream(file)
      if (src == null)
        throw new LoadException("Could not load image input stream.")
      try {
        reader.setInput(src)
        
        // First read images.
        val indices = (1 until reader.getNumImages(true)) // layer 0 is the flattened image, so start with 1
        val images = indices.map(i => SwingFXUtils.toFXImage(reader.read(i), null))
        
        // Then load metadata XML.
        val meta = reader.getImageMetadata(0)
        val xml = toScalaXml(meta.getAsTree(PSDMetadata.NATIVE_METADATA_FORMAT_NAME))
        
        (images, xml)
      } finally {
        src.close()
      }
    }
    
    Try(unsafe(reader, file)).recoverWith {
      case t => Failure(new LoadException("Failed to read image.", t))
    }
  }
  
  def toScalaXml(dom: org.w3c.dom.Node): Node = {
    val source = new DOMSource(dom)
    val adapter = new NoBindingFactoryAdapter
    val saxResult = new SAXResult(adapter)
    val transformerFactory = javax.xml.transform.TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.transform(source, saxResult)
    adapter.rootElem
  }
}

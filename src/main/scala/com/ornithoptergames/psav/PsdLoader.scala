package com.ornithoptergames.psav

import java.awt.image.BufferedImage

import java.io.File

import scala.util.Failure
import scala.util.Try
import scala.xml.Node
import scala.xml.parsing.NoBindingFactoryAdapter

import com.ornithoptergames.psav.FrameInfoLoader._
import com.twelvemonkeys.imageio.plugins.psd.PSDImageReader
import com.twelvemonkeys.imageio.plugins.psd.PSDMetadata

import javafx.embed.swing.SwingFXUtils
import javax.imageio.ImageIO
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.sax.SAXResult

object PsdLoader extends FileLoader {

  def main(args: Array[String]): Unit = {
    load(new File("c:/temp/explosion.psd"))
      .map(println)
      .recover({ case t => t.printStackTrace() })
  }
  
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
      Try(unsafe(reader, file)).recoverWith {
        case t => Failure(new LoadException("Failed to read image.", t))
      }
    }
    
    private[this] def unsafe(reader: PSDImageReader, file: File) = {
      // Let's stick all this ugly stateful, exception-throwing code in here...
      val src = ImageIO.createImageInputStream(file)
      if (src == null)
        throw new LoadException("Could not load image input stream.")
      
      try {
        reader.setInput(src)
        
        // Load metadata XML.
        val meta = reader.getImageMetadata(0)
        val xml = toScalaXml(meta.getAsTree(PSDMetadata.NATIVE_METADATA_FORMAT_NAME))
        
        val header = (xml \ "Header")
        val width = (header \ "@width").text.toInt
        val height = (header \ "@height").text.toInt
        
        // Interpret metadata and read images; layers are 1-indexed. 
        import MetaTreeStructures._
        val builder = (xml \ "Layers" \ "LayerInfo" zipWithIndex)
          .map({ case (node, index) => 
            if ((node \ "@pixelDataIrrelevant").text != "true") {
              Layer(node, index)(reader)
            } else ((node \ "@name").text match {
              case "</Layer group>" => CloseGroup
              case n => OpenGroup(n)
            })
          })
          .foldRight[FramesBuilder] (EmptyBuilder) { case (meta, builder) => builder.next(meta) }
        
        if (!builder.valid)
          throw new LoadException("Could not interpret metadata.")
        
        FrameInfo(Size(width, height), builder.frames)
        
      } finally {
        src.close()
      }
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
  
  /** Classes to interpret the <Layers> metadata. This is necessary because the XML is not nested: 
    * layers and layer groups are given in a flat list with "open" and "close" entries denoting the
    * group boundaries. And since groups *can* be nested (in Photoshop), we have to figure out the
    * nesting manually. So reading the metadata for layers is done in two steps:
    *  1. identify each <LayerInfo> as either a true layer, an open-group, or a close-group.
    *  2. utilize a modal builder pattern to load un-grouped layers normally and flatten grouped layers
    *     into one frame. 
    */
  private object MetaTreeStructures {
    
    /** Categorize each <LayerInfo> entry by type. */
    trait LayerMeta
    case class Layer(node: Node, index: Int)(reader: PSDImageReader) extends LayerMeta {
      val image = reader.read(index + 1)
      val name = (node \ "@name").text
      val top = (node \ "@top").text.toInt
      val bottom = (node \ "@bottom").text.toInt
      val left = (node \ "@left").text.toInt
      val right = (node \ "@right").text.toInt
      
      def frame: Frame = Frame(SwingFXUtils.toFXImage(image, null), name, top, bottom, left, right)
    }
    case object CloseGroup extends LayerMeta
    case class OpenGroup(name: String) extends LayerMeta
    
    /** Modal builders. */
    trait FramesBuilder {
      def frames: List[Frame]
      def next(meta: LayerMeta): FramesBuilder = meta match {
        case x @ Layer(_, _) => Normal(x.frame :: frames)
        case OpenGroup(name) => Open(frames, name)
        case _ => Invalid
      }
      def valid: Boolean
    }
    
    /** The initial builder. */
    case object EmptyBuilder extends FramesBuilder {
      val frames = List.empty[Frame]
      val valid = false
    }
    
    /** A builder which has just loaded a normal, root-level layer. */
    case class Normal(val frames: List[Frame]) extends FramesBuilder {
      val valid = true
    }
    
    /** A builder working on an open group. */
    case class Open(val frames: List[Frame], name: String) extends FramesBuilder {
      val valid = false
      
      private var depth = 1
      private var layers = List.empty[Layer]
      
      override def next(meta: LayerMeta): FramesBuilder = meta match {
        case x @ Layer(_, _) => layers = x :: layers; this
        case OpenGroup(name) => depth += 1; this
        case CloseGroup => depth -= 1
          if (depth > 0) this
          else Normal(this.frame :: frames)
      }
      
      private def frame: Frame = {
        val top    = layers.map(_.top).min
        val bottom = layers.map(_.bottom).max
        val left   = layers.map(_.left).min
        val right  = layers.map(_.right).max
        
        val image = new BufferedImage(right - left, bottom - top, BufferedImage.TYPE_INT_ARGB)
        val g = image.getGraphics()
        layers.foreach { x => g.drawImage(x.image, x.left - left, x.top - top, null) }
        
        Frame(SwingFXUtils.toFXImage(image, null), name, top, bottom, left, right)
      }
    }
    
    /** The metadata appears to be malformed. */
    case object Invalid extends FramesBuilder {
      val frames = List.empty
      val valid = false
      override def next(meta: LayerMeta) = this
    }
  }
}

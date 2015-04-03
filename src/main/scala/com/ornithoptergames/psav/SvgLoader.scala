package com.ornithoptergames.psav

import java.io.ByteArrayInputStream
import java.io.File

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.UnprefixedAttribute
import scala.xml.XML
import scala.xml.transform.RewriteRule
import scala.xml.transform.RuleTransformer

import com.ornithoptergames.psav.FrameInfoLoader._
import com.twelvemonkeys.imageio.plugins.svg.SVGImageReader

import javafx.embed.swing.SwingFXUtils
import javax.imageio.ImageIO

/** Reads an SVG image and rasterize it into the FrameInfo format.
  * Assumption: a layer (or frame) is represented in the SVG XML as 
  * all <g/> that are a direct descendant of the root <svg/>
  * 
  * Note: this works by tearing the original XML to shreds,
  * gluing it back together, and rendering one layer at a time as
  * though they were separate SVGs all along. Not sure if crazy
  * or brilliant. 
  */
object SvgLoader extends FileLoader {
  
  def main(args: Array[String]): Unit = { load(new File("c:/Users/Tyler/Desktop/ball.svg")) }
  
  override def load(file: File): Try[FrameInfo] = delegate.load(file)
  
  /* We know  when the program starts whether or not we have a reader for a given format.
   * Instead of having to pepper in checks for that each time, possibly doing a lot of pointless
   * work in the process, just store a delegate which knows whether the reader exists or not.
   */
  private lazy val delegate: FileLoader = {
    val rs = ImageIO.getImageReadersByFormatName("svg")
    if (rs.hasNext()) new Delegate(rs.next().asInstanceOf[SVGImageReader])
    else FailedDelegate
  }
  
  private object FailedDelegate extends FileLoader {
    val failure = Failure(new LoadException("ImageIO reader not found."))
    override def load(file: File): Try[FrameInfo] = failure
  }
  
  private class Delegate(reader: SVGImageReader) extends FileLoader {
    
    override def load(file: File): Try[FrameInfo] = {
      val xml = loadXml(file)
      val w: Double = (xml \ "@width").text.toDouble
      val h: Double = (xml \ "@height").text.toDouble
      val stripped = stripLayers(xml)
      
      val layerInfos = findLayers(xml).flatMap({ layerXml =>
        val name = nonEmpty(layerXml \ inkscapeLabelAttrib)
          .orElse(nonEmpty(layerXml \ idAttrib))
          .getOrElse("layer")
        
        val fixedLayerXml = fixLayerVisibility(layerXml)
        recombine(stripped, fixedLayerXml).map { slxml => LayerInfo(slxml, name, w, h) }
      })
      
      rasterize(layerInfos).map(frames => FrameInfo(Size(w, h), frames))
    }
    
    def rasterize(layers: Seq[LayerInfo]): Try[List[Frame]] = {
      
      // Let's stick all this ugly stateful, exception-throwing code in here...
      def unsafeRasterize(layer: LayerInfo) = {
        val bais = new ByteArrayInputStream(layer.layerXml.toString().getBytes())
        val src = ImageIO.createImageInputStream(bais)
        reader.setInput(src)
        val image = SwingFXUtils.toFXImage(reader.read(0), null)
        Frame(image, layer.name, 0, layer.height, 0, layer.width)
      }
      
      /* Rasterize each frame. This would naturally result in a Seq[Try[Frame]] 
       * so convert that into a Try[Seq[Frame]] by treating the first failure as fatal.
       */
      val zero: Try[List[Frame]] = Success(List.empty)
      layers.foldRight(zero)({
        case (layer, f @ Failure(_)) => f
        case (layer, Success(xs))    =>
          Try(unsafeRasterize(layer)) match {
            case Success(frame) => Success(frame :: xs)
            case Failure(t)     => Failure(t)
          }
      }).recoverWith {
        case t => Failure(new LoadException("Failed to read image.", t))
      }
    }
  }
  
  // Types so we don't accidentally trip over all these Nodes.
  private type FullXml = Node
  private type DelayeredXml = Node
  private type LayerNode = Node
  private type SingleLayerXml = Node
  
  private case class LayerInfo(layerXml: SingleLayerXml, name: String, width: Double, height: Double)
  
  // Loads the XML from a file.
  private def loadXml(file: File): FullXml = XML.loadFile(file)
  
  // Returns the layer nodes.
  private def findLayers(xml: FullXml): Seq[LayerNode] = (xml \ "g")
  
  // Removes all layers from the XML.
  private def stripLayers(root: FullXml): DelayeredXml = {
    val stripLayers = new RewriteRule {
      val layers = findLayers(root)
      override def transform(node: Node): NodeSeq = node match {
        case n if layers.contains(n) => NodeSeq.Empty
        case n => n
      }
    }
    new RuleTransformer(stripLayers).transform(root).head
  }
  
  /* Updates a node's style attribute from "display:none" to "display:inline".
   * In Inkscape, hidden layers are given "display:none", which effectively
   * removes them from rasterization. This is against anim-view's design intention. */
  private def fixLayerVisibility(layer: LayerNode): LayerNode = layer match {
    case e @ Elem(p,l, attribs ,s,c @ _*) =>
      val style = attribs.get("style").map(_.text)
      if (style.isEmpty) e
      else {
        val newStyle = style.get.replaceAll("""display\s*\:\s*none""", "display:inline")
        val newAttribs = new UnprefixedAttribute("style", newStyle, attribs.remove("style"))
        Elem(p,l, newAttribs ,s,c: _*)
      }
    case n => n
  }
  
  // Creates SVG XML that represents just a single layer. 
  private def recombine(root: DelayeredXml, layer: LayerNode): SingleLayerXml = root match {
    // Elem(prefix, label, attribs, scope, children)
    case Elem(p,l,a,s, children @ _*) => Elem(p,l,a,s, (children ++ layer): _*)
    case n => n
  }
  
  lazy val inkscapeLabelAttrib = "@{http://www.inkscape.org/namespaces/inkscape}label"
  lazy val idAttrib = "@id"
  
  def nonEmpty(n: NodeSeq): Option[String] = nonEmpty(n.text)
  def nonEmpty(s: String): Option[String] = if (s.isEmpty()) None else Some(s)
}
package com.ornithoptergames.psav

import java.io.File

import scala.util.Failure
import scala.util.Try
import scala.util.matching.Regex

import FrameInfoLoader._
import akka.actor.Actor

trait FileLoader {
  def load(file: File): Try[FrameInfo]
}

object FrameInfoLoader {
  val actorName = "frame-info-loader"
  
  case class Load(file: File, layerNameFilters: List[Regex])
  type LoadResult = Try[FrameInfo]
  
  class LoadException(message: String = null, cause: Throwable = null) extends Exception(message, cause)
}

class FrameInfoLoader extends Actor {
  
  def receive = {
    case Load(file, filters) =>
      val result = loadByExtension(file).map { info => applyFilter(info, filters) }
      sender ! result
  }
  
  private[this] def extension(file: File) = file.getName().takeRight(3).toLowerCase()
  
  private[this] def loadByExtension(file: File): Try[FrameInfo] =
    extension(file) match {
      case "psd" => PsdLoader.load(file)
      case "svg" => SvgLoader.load(file)
      case _     => Failure(new LoadException("Unsupported file extension."))
    }
  
  private[this] def applyFilter(frameInfo: FrameInfo, filters: List[Regex]) = {
    lazy val exclude = (f: Frame) => filters.exists { _.pattern.matcher(f.name).matches() }
    val frames = frameInfo.frames.filterNot(exclude)
    FrameInfo(frameInfo.size, frames)
  }
}
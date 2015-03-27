package com.ornithoptergames.psav

import java.io.File

import scala.util.matching.Regex

import scalafx.scene.paint.Color

object Messages {
  val fps = RxMessage[Double](Config.defaultFps)
  val fpsUp = RxMessage.impulse()
  val fpsDown = RxMessage.impulse()
  
  val frameFilter = RxMessage[List[Regex]](Config.defaultFilters)
  
  val newFile = RxMessage[File]()
  val updateFile = RxMessage[File]()
  
  val newFrames = RxMessage[FrameInfo]()
  val updateFrames = RxMessage[FrameInfo]()
  
  val bgColor = RxMessage[Color](Config.defaultBgColor)
  
  val play = RxMessage.impulse()
  val pause = RxMessage.impulse()
  
  val animationLoaded = RxMessage.impulse()
  val animationPlaying = RxMessage.impulse()
  val animationPaused = RxMessage.impulse()
}

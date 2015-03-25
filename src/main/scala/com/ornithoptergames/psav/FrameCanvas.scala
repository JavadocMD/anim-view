package com.ornithoptergames.psav

import akka.actor.ActorSystem
import akka.actor.FSM
import akka.actor.Props
import scalafx.Includes._
import scalafx.animation.KeyFrame
import scalafx.animation.Timeline
import scalafx.scene.canvas.Canvas
import scalafx.scene.image.Image
import scalafx.scene.paint.Color
import scalafx.util.Duration

import FrameCanvasFsm._

abstract class FrameCanvas(implicit system: ActorSystem) extends Canvas {
  val fsm = system.actorOf(Props(new FrameCanvasFsm(this)), "canvas-fsm")
  
  val gc = graphicsContext2D
  gc.fill = Color.White
  gc.stroke = Color.Black
  
  def setBgColor(color: Color): Unit = { gc.fill = color }
  
  def updateSize(f: FrameInfo) = {
    width = f.size.w + 2
    height = f.size.h + 2
    visible = true
  }
  
  def timeline(fps: Double, frameInfo: FrameInfo): Timeline = {
    val frameDur = Duration(1000d / fps)
    val fs = frameInfo.frames
    val w = frameInfo.size.w
    val h = frameInfo.size.h
    val drawFrame = (frame: Frame) => {
      gc.clearRect(0, 0, w + 2, h + 2)
      gc.fillRect(0, 0, w + 2, h + 2)
      gc.strokeRect(0, 0, w + 2, h + 2)
      gc.drawImage(new Image(frame.image), frame.left + 1, frame.top + 1)
    }
    
    new Timeline {
      cycleCount = Timeline.Indefinite

      val is = (0 to fs.length)
      keyFrames = (is zip is.map(fs.lift)) map {
        case (i, Some(f)) => KeyFrame(frameDur * i, "", () => drawFrame(f))
        case (i, None)    => KeyFrame(frameDur * i, "", () => { /*noop*/ })
      }
    }
  }
  
  // For use as callbacks from the FSM
  def playing(): Unit
  def paused(): Unit
  def loaded(): Unit
}



object FrameCanvasFsm {
  sealed trait State
  case object Initial extends State
  case object Loaded  extends State
  case object Paused  extends State
  case object Playing extends State
  
  sealed trait Message
  case class SetFps(fps: Double) extends Message {
    lazy val millisPerFrame: Double = 1000d / fps
    lazy val secondsPerFrame: Double = 1d / fps
  }
  case class SetFrames(frameInfo: FrameInfo) extends Message
  case object Play  extends Message
  case object Pause extends Message
  
  sealed trait Data
  case object NoData extends Data
  case class Fps(fps: Double) extends Data
  case class Frames(frameInfo: FrameInfo) extends Data
  case class Full(fps: Double, frameInfo: FrameInfo, timeline: Timeline) extends Data {
    /* Note: these side-effecting methods are sort of bad form, 
     * but FSM isn't firing same-state transitions in this version, so it's kind of necessary.
     */
    def playing(): Full = { timeline.play(); this }
    def paused(): Full = { timeline.pause(); this }
  }
}

class FrameCanvasFsm(canvas: FrameCanvas)
  extends FSM[State, Data] {
  
  startWith(Initial, NoData)
  
  when(Initial) {
    case Event(SetFps(fps), NoData | Fps(_)) => stay() using Fps(fps)
    case Event(SetFps(fps), Frames(fi))      => goto(Loaded) using timelineData(fps, fi)
    
    case Event(SetFrames(fi), NoData | Frames(_)) => stay() using Frames(fi)
    case Event(SetFrames(fi), Fps(fps))           => goto(Loaded) using timelineData(fps, fi)
    case _ => stay()
  }
  
  when(Loaded) {
    case Event(SetFps(fps), Full(_,fi,t))    => stay() using timelineData(fps, fi, t)
    case Event(SetFrames(fi), Full(fps,_,t)) => goto(Loaded) using timelineData(fps, fi, t)
    case Event(Play, f @ Full(_,_,_))        => goto(Playing) using f.playing()
    case _ => stay()
  }
  
  when(Playing) {
    case Event(Pause, f @ Full(_,_,_))       => goto(Paused) using f.paused()
    case Event(SetFps(fps), Full(_,fi,t))    => goto(Playing) using timelineData(fps, fi, t).playing()
    case Event(SetFrames(fi), Full(fps,_,t)) => goto(Loaded) using timelineData(fps, fi, t)
    case _ => stay()
  }
  
  when(Paused) {
    case Event(Play, f @ Full(_,_,_))        => goto(Playing) using f.playing()
    case Event(SetFps(fps), Full(_,fi,t))    => goto(Paused) using timelineData(fps, fi, t).paused()
    case Event(SetFrames(fi), Full(fps,_,t)) => goto(Loaded) using timelineData(fps, fi, t)
    case _ => stay()
  }
  
  onTransition {
    case s -> Playing if s != Playing => canvas.playing()
    case s -> Paused  if s != Paused  => canvas.paused()
    case s -> Loaded  if s != Loaded  => canvas.loaded()
  }
  
  def timelineData(fps: Double, frameInfo: FrameInfo, oldTimeline: Timeline): Full = {
    // Note: more nasty side-effecting to get around lack of FSM same-state transitions.
    oldTimeline.stop() 
    timelineData(fps, frameInfo)
  }
    
  def timelineData(fps: Double, frameInfo: FrameInfo): Full = {
    // Note: more nasty side-effecting to get around lack of FSM same-state transitions.
    canvas.updateSize(frameInfo)
    Full(fps, frameInfo, canvas.timeline(fps, frameInfo))
  }
}
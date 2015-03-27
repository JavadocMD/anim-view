package com.ornithoptergames.psav

import FrameCanvasFsm._

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

abstract class FrameCanvas(implicit system: ActorSystem) extends Canvas {
  val fsm = system.actorOf(Props(new FrameCanvasFsm(this)), "canvas-fsm")
  
  val gc = graphicsContext2D
  gc.fill = Color.White
  gc.stroke = Color.Black
  
  def setBgColor(color: Color): Unit = {
    gc.fill = color
    // We can't see the color change unless we're playing.
    fsm ! Play
  }
  
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
  case class NewFrames(frameInfo: FrameInfo) extends Message
  case class UpdateFrames(frameInfo: FrameInfo) extends Message
  case object Play  extends Message
  case object Pause extends Message
  
  sealed trait Data
  case object NoData extends Data
  case class Fps(fps: Double) extends Data
  case class Frames(frameInfo: FrameInfo) extends Data
  case class Full(fps: Double, frameInfo: FrameInfo, timeline: Timeline) extends Data
}

class FrameCanvasFsm(canvas: FrameCanvas)
  extends FSM[State, Data] {
  
  startWith(Initial, NoData)
  
  when(Initial) {
    case Event(SetFps(fps), NoData | Fps(_)) => stay() using Fps(fps)
    case Event(SetFps(fps), Frames(fi))      => goto(Playing) using playing(fps, fi)
    
    case Event(NewFrames(fi), NoData | Frames(_)) => stay() using Frames(fi)
    case Event(NewFrames(fi), Fps(fps))           => goto(Playing) using playing(fps, fi)
    
    case Event(UpdateFrames(fi), NoData | Frames(_)) => stay() using Frames(fi)
    case Event(UpdateFrames(fi), Fps(fps))           => goto(Playing) using playing(fps, fi)
    case _ => stay()
  }
  
  when(Playing) {
    case Event(Pause, f @ Full(_,_,_))          => goto(Paused)  using paused(f)
    case Event(SetFps(fps), Full(_,fi,t))       => goto(Playing) using playing(fps, fi, t)
    case Event(NewFrames(fi), Full(fps,_,t))    => goto(Playing) using playing(fps, fi, t)
    case Event(UpdateFrames(fi), Full(fps,_,t)) => goto(Playing) using playing(fps, fi, t)
    case _ => stay()
  }
  
  when(Paused) {
    case Event(Play, f @ Full(_,_,_))           => goto(Playing) using playing(f)
    case Event(SetFps(fps), Full(_,fi,t))       => goto(Paused)  using paused(fps, fi, t)
    case Event(NewFrames(fi), Full(fps,_,t))    => goto(Playing) using playing(fps, fi, t)
    case Event(UpdateFrames(fi), Full(fps,_,t)) => goto(Paused)  using paused(fps, fi, t)
    case _ => stay()
  }
  
  
  /* Note: What follows is a kind of sloppy way to make the above event handlers cleaner
   * and at the same time compensate for this version of Akka FSM which doesn't support
   * same-state onTransition handlers. All the side-effecting -- manipulating the 'timeline',
   * emitting reactive messages, and resizing -- is handled by these data constructor methods.
   */
  
  private[this] def playing(fps: Double, frameInfo: FrameInfo, oldTimeline: Timeline): Full = {
    oldTimeline.stop() 
    playing(fps, frameInfo) // chain to next
  }
  
  private[this] def playing(fps: Double, frameInfo: FrameInfo): Full = {
    val data = Full(fps, frameInfo, canvas.timeline(fps, frameInfo))
    canvas.updateSize(data.frameInfo)
    canvas.loaded()
    playing(data) // chain to next
  }
  
  private[this] def playing(data: Full): Full = {
    data.timeline.play()
    canvas.playing()
    data
  }
  
  private[this] def paused(fps: Double, frameInfo: FrameInfo, oldTimeline: Timeline): Full = {
    oldTimeline.stop() 
    paused(fps, frameInfo) // chain to next
  }
  
  private[this] def paused(fps: Double, frameInfo: FrameInfo): Full = {
    val data = Full(fps, frameInfo, canvas.timeline(fps, frameInfo))
    canvas.updateSize(data.frameInfo)
    canvas.loaded()
    paused(data) // chain to next
  }
  
  private[this] def paused(data: Full): Full = {
    data.timeline.pause()
    canvas.paused()
    data
  }
}
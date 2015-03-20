package com.ornithoptergames.psav

import Messages._
import akka.actor.Actor.Receive
import akka.actor.ActorSystem
import akka.actor.FSM
import akka.actor.Props
import scalafx.Includes._
import scalafx.animation.KeyFrame
import scalafx.animation.Timeline
import scalafx.scene.canvas.Canvas
import scalafx.scene.image.Image
import scalafx.scene.paint.Color
import scalafx.scene.paint.Color._
import scalafx.util.Duration
import FrameCanvasFsm._

class FrameCanvas(implicit system: ActorSystem, msg: Messaging) extends Canvas {
  val actor = system.actorOf(Props(new FrameCanvasFsm(this)), "canvas")
  msg.subscribe(fps_?, actor)
  msg.subscribe(frames_?, actor)
  msg.subscribe(canvas_?, actor)
  
  msg.subscribe(canvas_?, receive { case SetBgColor(color) => setBgColor(color) })
  
  val gc = graphicsContext2D
  gc.fill = White
  gc.stroke = Black
  
  def setBgColor(color: Color): Unit = {
    gc.fill = color
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
}



object FrameCanvasFsm {
  sealed trait State
  case object Initial extends State
  case object Loaded  extends State
  case object Paused  extends State
  case object Playing extends State
  
  sealed trait Data
  case object NoData extends Data
  case class Fps(fps: Double) extends Data
  case class Frames(frameInfo: FrameInfo) extends Data
  case class Full(fps: Double, frameInfo: FrameInfo, timeline: Timeline) extends Data {
    def playing(): Full = { timeline.play(); this }
    def paused(): Full = { timeline.pause(); this }
  }
}

class FrameCanvasFsm(canvas: FrameCanvas)(implicit msg: Messaging)
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
    case s -> Playing if s != Playing => msg.publish(AnimationPlaying, self)
    case s -> Paused  if s != Paused  => msg.publish(AnimationPaused, self)
    case s -> Loaded  if s != Loaded  => msg.publish(AnimationLoaded, self)
  }
  
  def timelineData(fps: Double, frameInfo: FrameInfo, oldTimeline: Timeline): Full = {
    oldTimeline.stop()
    timelineData(fps, frameInfo)
  }
    
  def timelineData(fps: Double, frameInfo: FrameInfo): Full = {
    canvas.updateSize(frameInfo)
    Full(fps, frameInfo, canvas.timeline(fps, frameInfo))
  }
}
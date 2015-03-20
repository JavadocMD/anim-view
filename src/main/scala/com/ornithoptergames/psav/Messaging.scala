package com.ornithoptergames.psav

import scala.collection.mutable

import java.io.File
import akka.actor.Actor
import akka.actor.Actor.Receive
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.EventBus
import akka.event.SubchannelClassification
import akka.util.Subclassification
import scalafx.scene.paint.Color

object Messages {
  sealed trait Message { def path: String }
  
  val file_?   = "?/file"
  val fps_?    = "?/ui/fps"
  val frames_? = "?/ui/canvas/frames"
  val canvas_? = "?/ui/canvas"
  val canvas_! = "!/ui/canvas"
  
  case class LoadFile(file: File) extends Message { val path = file_? }
  
  case class SetFps(fps: Double) extends Message {
    val path = fps_?
    
    lazy val millisPerFrame: Double = 1000d / fps
    lazy val secondsPerFrame: Double = 1d / fps
  }
  
  case object FpsUp   extends Message { val path = fps_? }
  case object FpsDown extends Message { val path = fps_? }
  
  case class SetFrames(frameInfo: FrameInfo) extends Message { val path = frames_? }
  
  case object Play  extends Message { val path = canvas_? }
  case object Pause extends Message { val path = canvas_? }
  case class SetBgColor(color: Color) extends Message { val path = canvas_? }
  
  case object AnimationLoaded  extends Message { val path = canvas_! }
  case object AnimationPlaying extends Message { val path = canvas_! }
  case object AnimationPaused  extends Message { val path = canvas_! }
}
import Messages._

class Messaging(val system: ActorSystem) {
  private val bus = new MessagingEventBus
  private var pubStrat: PublishingStrategy = new Holding(bus)
  
  private sealed class Subscription(f: Receive) extends Actor {
    override def receive = f
  }
  
  def subscribe(path: String, f: Receive): ActorRef = {
    val actor = system.actorOf(Props(new Subscription(f)))
    bus.subscribe(actor, path)
    actor
  }
  
  def subscribe(path: String, actor: ActorRef): ActorRef = {
    bus.subscribe(actor, path)
    actor
  }
  
  def unsubscribe(subscriber: ActorRef): Unit = bus.unsubscribe(subscriber)
  
  def publish[M <: Message](msg: M): Unit = publish(msg, ActorRef.noSender)
  
  def publish[M <: Message](msg: M, sender: ActorRef): Unit = { pubStrat.publish(msg, sender) }
  
  def init(): Unit = { pubStrat = pubStrat.flush() }
}

private class MessagingEventBus extends EventBus with SubchannelClassification {
  type Event = (Message, ActorRef)
  type Classifier = String
  type Subscriber = ActorRef

  override protected def classify(event: Event): Classifier = event._1.path

  protected def subclassification = new Subclassification[Classifier] {
    def isEqual(x: Classifier, y: Classifier) = x == y
    def isSubclass(x: Classifier, y: Classifier) = x.startsWith(y)
  }

  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber.tell(event._1, event._2)
  }
}

private trait PublishingStrategy {
  def publish[M <: Message](msg: M, sender: ActorRef): Unit
  def flush(): PublishingStrategy
}
private class Holding(bus: MessagingEventBus) extends PublishingStrategy {
  import scala.collection.mutable
  private val held: mutable.ListBuffer[(Message, ActorRef)] = mutable.ListBuffer.empty
  
  def publish[M <: Message](msg: M, sender: ActorRef): Unit = {
    held += ((msg, sender))
  }
  
  def flush(): PublishingStrategy = {
    held.foreach { bus.publish(_) }
    new PassThrough(bus)
  }
}
private class PassThrough(bus: MessagingEventBus) extends PublishingStrategy {
  
  def publish[M <: Message](msg: M, sender: ActorRef): Unit = bus.publish((msg, sender))
  
  def flush(): PublishingStrategy = this
}
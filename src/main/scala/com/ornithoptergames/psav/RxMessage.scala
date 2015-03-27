package com.ornithoptergames.psav

import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import rx.lang.scala.Observable
import rx.lang.scala.Subject
import rx.lang.scala.Subscription
import rx.lang.scala.observables.ConnectableObservable
import rx.lang.scala.subjects.BehaviorSubject
import rx.lang.scala.subjects.SerializedSubject

/** `RxMessage` provides adaptations of `Subject` to provide a typed pub/sub messaging system.
  * Each instance of `RxMessage` defines one kind of message (optionally with a typed payload) that the 
  * system can emit and/or listen for.
  */
object RxMessage {
  
  /** A message with no default value. */
  def apply[T](): RxMessage[T] = new RxMessage(Subject())
  
  /** A message with a default value. Subscribers will get one value regardless of when they subscribe. */
  def apply[T](default: T): RxMessage[T] = new RxMessage(BehaviorSubject(default))
  
  /** A basic message for when there is no useful payload, and we only care about *when* the messages fire. */
  sealed trait ImpulseMessage
  final case object Impulse extends ImpulseMessage
  
  /** An impulse message. */
  def impulse() = new ImpulseRxMessage(Subject())
  
  object Implicits {
    
    implicit class ActorForwardable[T](obs: Observable[T]) {
      def forwardTo(actor: ActorRef) = obs.subscribe(t => actor ! t)
      def forwardTo(actor: ActorRef, as: T => Any) = obs.subscribe(t => actor ! as(t))
    }
  }
}

/** The basic `RxMessage` implementation decorates a given `Subject`, selectively hiding and exposing
  * functionality relevant to a pub/sub messaging system.
  * 
  * `RxMessage` guarantees that any thread can safely publish messages (using `SerializedSubject`).
  * `RxMessage` also exposes a `ConnectableObservable` that has already been connected, since we want
  * message processing to happen in "real time", regardless of when subscribers come and go.
  */
class RxMessage[T](wrappedSubject: Subject[T]) {
  val subject: Subject[T] = SerializedSubject(wrappedSubject)
  def observable: ConnectableObservable[T] = { val o = subject.publish; o.connect; o }
  
  def publish(msg: T): Unit = subject.onNext(msg)
  def subscribe(onNext: T => Unit): Subscription = observable.subscribe(onNext)
  def forwardTo(actor: ActorRef, as: T => Any): Subscription = observable.subscribe(x => actor ! as(x))
}

/** `ImpulseRxMessage` implements a message without any payload. To save typing, a few methods
  * from `RxMessage` are given simplified counterparts.
  */
class ImpulseRxMessage(wrappedSubject: Subject[RxMessage.ImpulseMessage]) 
    extends RxMessage[RxMessage.ImpulseMessage](wrappedSubject) {
  
  def publish(): Unit = publish(RxMessage.Impulse)
  def forwardTo(actor: ActorRef, asJust: Any): Subscription = observable.subscribe(x => actor ! asJust)
}

/** A utility for keeping track of only the most recent value of an observable.
  * This is probably cheating in RxScala terms, but it's useful in combining observables
  * to only send messages when a master observable does.
  */
class MostRecent[T](obs: Observable[T]) {
  private[this] var mostRecent: Option[T] = None
  obs.subscribe { t => mostRecent = Option(t) }
  
  def value: Option[T] = mostRecent
  def get: T = mostRecent.get
}
package com.ornithoptergames.psav

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import rx.lang.scala.Observable
import rx.lang.scala.Subject
import rx.lang.scala.Subscription
import rx.lang.scala.observables.ConnectableObservable
import rx.lang.scala.subjects.BehaviorSubject
import rx.lang.scala.subjects.SerializedSubject

/** `RxMessage` provides some nice functionality around `Subject`s to provide a typed pub/sub messaging system.
  * Each instance of `RxMessage` defines one kind of message (optionally with a typed payload) that the 
  * system can emit and/or listen for.
  */
object RxMessage {
  
  /** A message with no default value. */
  def apply[T](): Subject[T] = SerializedSubject(Subject())
  
  /** A message with a default value. Subscribers will get one value regardless of when they subscribe. */
  def apply[T](default: T): Subject[T] = SerializedSubject(BehaviorSubject(default))
  
  /** A basic message for when there is no useful payload, and we only care about *when* the messages fire. */
  final case object Impulse
  
  /** An impulse message. */
  def impulse() = SerializedSubject(Subject[Impulse.type]())
  
  object Implicits {
    
    implicit class ActorForwardable[T](obs: Observable[T]) {
      
      def forwardTo[S](subject: Subject[S], as: T => S) = obs.subscribe(t => subject onNext as(t))
      
      def forwardTo(actor: ActorRef) = obs.subscribe(t => actor ! t)
      
      def forwardTo(actor: ActorRef, asJust: Any) = obs.subscribe(t => actor ! asJust)
      
      def forwardTo(actor: ActorRef, as: T => Any) = obs.subscribe(t => actor ! as(t))
      
      /** Pipe messages to `actor`, expecting its response as a Try. Unwrap the Try
        * by forwarding the contents of Success to another RxMessage, `then`, or
        * by calling `recover` on Failure.
        */
      def pipeThrough[A](actor: ActorSelection, then: Subject[A], recover: PartialFunction[Throwable, Unit])
          (implicit timeout: Timeout, executor: ExecutionContext) = {
        
        obs.subscribe { t =>
          val future: Future[Try[A]] = (actor ? t).asInstanceOf[Future[Try[A]]]
          FutureTry(future).flattenTry.map(m => then.onNext(m)).recover(recover)
        }
      }
    }
  }
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
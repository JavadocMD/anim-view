package com.ornithoptergames

import scalafx.geometry.Insets
import akka.actor.Actor

package object psav {

  def receive(body: PartialFunction[Any, Unit]): Actor.Receive = body 
  
  object Bottom {
    def apply(bottom: Double) = Insets(0, 0, bottom, 0)
  }
}
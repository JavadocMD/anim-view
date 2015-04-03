package com.ornithoptergames

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import scalafx.geometry.Insets
import scalafx.scene.Node
import scalafx.scene.control.Control

package object psav {

  implicit class FutureTry[T](f: Future[Try[T]])(implicit executor: ExecutionContext) {
    def flattenTry: Future[T] = f.flatMap {
      case Success(s) => Future.successful(s)
      case Failure(t) => Future.failed(t)
    }
  }
  
  implicit class HasMargin[N <: Node](node: N) {
    def withMargin(insets: Insets): N = {
      node.margin = insets
      node
    }
  }
  
  def forceSize(n: Control, width: Double, height: Double) = {
    n.prefWidth = width
    n.minWidth = width
    n.maxWidth = width
    n.prefHeight = height
    n.minHeight = height
    n.maxHeight = height
  }
  
  object LeftInset { def apply(left: Double) = Insets(0,0,0,left) }
  
  object RightInset { def apply(right: Double) = Insets(0,right,0,0) }
  
  object TopInset { def apply(top: Double) = Insets(top,0,0,0) }
  
  object BottomInset { def apply(bottom: Double) = Insets(0,0,bottom,0) }
}
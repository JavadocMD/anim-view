package com.ornithoptergames

import scalafx.geometry.Insets
import scalafx.scene.Node
import scalafx.scene.control.Control

package object psav {

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
  
  object Left { def apply(left: Double) = Insets(0,0,0,left) }
  
  object Right { def apply(right: Double) = Insets(0,right,0,0) }
  
  object Top { def apply(top: Double) = Insets(top,0,0,0) }
  
  object Bottom { def apply(bottom: Double) = Insets(0,0,bottom,0) }
}
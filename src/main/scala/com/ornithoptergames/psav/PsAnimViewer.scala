package com.ornithoptergames.psav

import akka.actor.ActorSystem
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.geometry.Insets
import scalafx.geometry.Pos
import scalafx.scene.Scene
import scalafx.scene.image.Image._
import scalafx.scene.layout.BorderPane
import scalafx.scene.layout.HBox
import scalafx.scene.layout.StackPane
import scalafx.stage.Stage

object PsAnimViewer extends JFXApp {
  implicit val system = ActorSystem("anim-view")
  implicit def implStage: Stage = stage
  
  val widgets = new Widgets
    
  stage = new JFXApp.PrimaryStage {
    this.title = "Animation Viewer"
    this.width = 1024
    this.height = 768
    
    Resources.icons.foreach { i => this.icons add i }
    
    this.scene = new Scene {
      this.stylesheets add "com/ornithoptergames/psav/style.css"
      
      this.root = bp
    }
    
    lazy val bp = new BorderPane {
      this.top = widgets.menu
      
      // The color picker is an invisible item (until it's activated); might as well put it here.
      this.left = widgets.colorPicker
      
      this.bottom = new HBox {
        this.styleClass add "button-panel"
        this.padding = Insets(20)
        this.alignment = Pos.Center
        
        this.children = Seq(
          widgets.playButton,
          widgets.pauseButton,
          widgets.fpsControl.withMargin(Left(20))
        )
      }

      this.center = new StackPane {
        this.styleClass add "image-panel"
        
        this.children = Seq(widgets.helpText, widgets.loading, widgets.canvas)
        this.alignment = Pos.Center
      }
    }
    
    this.onShown = () => {
      // stop the FPS input from being focused on startup
      bp.requestFocus()
      // prevent canvas from covering menu
      widgets.menu.toFront()
    }
    
    this.onCloseRequest = () => {
      widgets.fileManager.fileWatcher.stop()
      system.shutdown()
    }
  }
}
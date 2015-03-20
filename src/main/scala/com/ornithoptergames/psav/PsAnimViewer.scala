package com.ornithoptergames.psav

import Messages._
import akka.actor.ActorSystem
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.geometry.Insets
import scalafx.geometry.Pos
import scalafx.scene.Scene
import scalafx.scene.layout.BorderPane
import scalafx.scene.layout.HBox
import scalafx.scene.layout.StackPane
import scalafx.scene.layout.VBox
import scalafx.stage.Stage
import scalafx.scene.layout.AnchorPane
import scalafx.scene.layout.Priority
import scalafx.scene.paint.Color
import akka.actor.Props
import scalafx.scene.control.MenuBar
import scalafx.scene.control.Menu
import scalafx.scene.control.MenuItem
import scalafx.scene.input.KeyCombination

object PsAnimViewer extends JFXApp {
  implicit val system = ActorSystem("ps-anim-viewer")
  implicit val msg = new Messaging(system)
  implicit def implStage: Stage = stage
  
  system.actorOf(Props(new FileManager(msg)))
  
  val widgets = new Widgets
    
  stage = new JFXApp.PrimaryStage {
    this.title = "Photoshop Animation Viewer"
    this.width = 1024
    this.height = 768
    
    this.scene = new Scene {
      this.stylesheets add "com/ornithoptergames/psav/style.css"
      
      this.root = bp
    }
    
    lazy val bp = new BorderPane {
      this.top = widgets.menu
      
      this.left = widgets.colorPicker
      
      this.bottom = new HBox {
        this.styleClass add "button-panel"
        this.padding = Insets(20)
        this.alignment = Pos.Center
        
        this.children = Seq(
          widgets.playButton,
          widgets.pauseButton,
          widgets.fpsLabel,
          widgets.fpsInput,
          widgets.fpsArrows
        )
      }

      this.center = new StackPane {
        this.styleClass add "image-panel"
        
        this.children = Seq(widgets.helpText, widgets.canvas)
        this.alignment = Pos.Center
      }
    }
    
    this.onCloseRequest = () => { system.shutdown() }
    
    this.onShown = () => {
      msg.init() // deliver messages that were logged during initialization
      bp.requestFocus() // stop the FPS input from being focused on startup
    }
  }
}
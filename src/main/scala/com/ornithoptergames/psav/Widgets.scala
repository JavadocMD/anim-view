package com.ornithoptergames.psav

import Messages._
import akka.actor.ActorSystem
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.scene.control.Button
import scalafx.scene.control.Label
import scalafx.scene.control.Slider
import scalafx.scene.layout.Priority
import scalafx.stage.FileChooser
import scalafx.stage.FileChooser.ExtensionFilter
import scalafx.stage.Stage
import scalafx.scene.control.ColorPicker
import scalafx.scene.paint.Color
import java.nio.file.WatchEvent.Kind
import java.io.IOException
import scalafx.scene.control.MenuBar
import scalafx.scene.control.Menu
import scalafx.scene.control.MenuItem
import scalafx.scene.input.KeyCombination
import scalafx.scene.input.KeyCode
import scalafx.scene.control.SeparatorMenuItem
import scalafx.scene.control.Tooltip
import scalafx.scene.text.Font
import scalafx.scene.text.Text
import java.net.URL
import scalafx.scene.text.TextAlignment
import scalafx.scene.control.TextField
import scalafx.scene.layout.VBox
import scalafx.geometry.Insets
import scalafx.geometry.Pos
import scalafx.scene.layout.Region
import scalafx.scene.image.Image
import scalafx.scene.image.ImageView
import scalafx.scene.control.ContentDisplay

object Resources {
  private def res(path: String): URL = this.getClass().getResource(path)
  private def resExt(path: String): String = res(path).toExternalForm()
  
  val fontawesome: Font = Font.loadFont(resExt("fontawesome-webfont.ttf"), 20)
  val play: Image = new Image(resExt("play.png"))
  val pause: Image = new Image(resExt("pause.png"))
}


class Widgets(implicit stage: Stage, system: ActorSystem, msg: Messaging) {
  
  val helpText = new Label {
    id = "helpText"
    text = "Ctrl+O to open a Photoshop file\nas an animated frame set."
    textAlignment = TextAlignment.Center
    
    msg.subscribe(canvas_!, receive {
      case AnimationLoaded => Platform.runLater { visible = false }
    })
  }
  
  
  val fileChooser = new FileChooser {
    title = "Open PSD File"
    extensionFilters += new ExtensionFilter("PSD Files", "*.psd")
  
    def openPsd()(implicit stage: Stage): Unit = 
      Option(showOpenDialog(stage)) foreach { f =>
        // Remember directory for next time.
        initialDirectory = f.getParentFile
        // Then message the file for loading.
        msg.publish(LoadFile(f))
      }
  }
  
  
  val menu = new MenuBar {
    
    this.menus = Seq(
      new Menu("_File") {
        this.mnemonicParsing = true
        
        this.items = Seq(
          new MenuItem("_Open...") {
            this.mnemonicParsing = true
            this.accelerator = KeyCombination("Ctrl+O")
            this.onAction = () => fileChooser.openPsd()
          },
          new MenuItem("_Exit") {
            this.mnemonicParsing = true
            this.accelerator = KeyCombination("Alt+F4")
            this.onAction = () => System.exit(0)
          }
        )
      },
      new Menu("_Edit") {
        this.mnemonicParsing = true
        
        this.items = Seq(
          new MenuItem("Set _Background Color") {
            this.mnemonicParsing = true
            this.accelerator = KeyCombination("Ctrl+B")
            this.onAction = () => colorPicker.show()
          }
        )
      }
    )
  }
  
  
  val pauseButton = new FontAwesomeButton("Pause", "\uf04c", "text-lg") {
    id = "pause"
    disable = true
    
    prefHeight = 60
    prefWidth  = 60
//    maxHeight = Region.USE_PREF_SIZE
//    maxWidth = Region.USE_PREF_SIZE
//    minHeight = Region.USE_PREF_SIZE
//    minWidth = Region.USE_PREF_SIZE
    
    msg.subscribe(canvas_!, receive {
      case AnimationPlaying => Platform.runLater { disable = false; requestFocus() }
      case AnimationPaused  => Platform.runLater { disable = true }
    })
    
    onAction = () => msg.publish(Pause)
  }
  
  
  val playButton = new FontAwesomeButton("Play","\uf04b", "text-lg") {
    id = "play"
    disable = true
    
    prefHeight = 60
    prefWidth  = 60
    
    msg.subscribe(canvas_!, receive {
        case AnimationPlaying => Platform.runLater { disable = true }
        case AnimationPaused  => Platform.runLater { disable = false; requestFocus() }
    })
    
    onAction = () => msg.publish(Play)
  }

  
  val fpsLabel = new Label("FPS") {
    id = "fps-label"
    rotate = -90
    margin = Insets(0, 0, 0, 20)
  }

  
  val fpsInput = new TextField {
    text = "12"
    prefColumnCount = 2
    margin = Insets(0, 2, 0, 0)
    prefHeight = 44
    prefWidth = 44
    
    val fpsFormat = "^([1-9][0-9]?)$".r
    
    def fps: Int = fpsFormat.findFirstIn(text.value).map(_.toInt).getOrElse(12)
    
    text.onChange((obsval, prev, now) => now match {
      case fpsFormat(x) => msg.publish(SetFps(now.toDouble))
      case _            => text = prev
    })
    
    msg.subscribe(fps_?, receive {
      case FpsUp   => Platform.runLater { text = (fps + 1).toString }
      case FpsDown => Platform.runLater { text = (fps - 1).toString }
    })
    
    msg.publish(SetFps(fps))
  }
  
  
  val fpsArrows = new VBox {
    this.spacing = 2
    this.alignment = Pos.Center
    this.children = Seq(
      new FontAwesomeButton("Up", "\uf0d8", "text-sm") {
        this.onAction = () => msg.publish(FpsUp)
      },
      new FontAwesomeButton("Down", "\uf0d7", "text-sm") {
        this.onAction = () => msg.publish(FpsDown)
      })
  }
  
  
  val colorPicker = new ColorPicker {
    value = Color.CornflowerBlue
    visible = false
    onAction = () => { msg.publish(SetBgColor(value.value)) }
    msg.publish(SetBgColor(value.value))
  }
  
  
  val canvas = new FrameCanvas { visible = false }
  
  
  class FontAwesomeButton(name: String, fontAwesomeIcon: String, iconClass: String) extends Button(name) {
    alignment = Pos.Center
    contentDisplay = ContentDisplay.GraphicOnly
    graphic = new Text(fontAwesomeIcon) {
      styleClass add iconClass
      font = Resources.fontawesome
    }
  }
}
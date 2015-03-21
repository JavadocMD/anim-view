package com.ornithoptergames.psav

import java.net.URL

import scala.concurrent.duration._

import akka.actor.ActorSystem
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.application.Platform.runLater
import scalafx.geometry.Insets
import scalafx.geometry.Pos
import scalafx.scene.control.Button
import scalafx.scene.control.ColorPicker
import scalafx.scene.control.ContentDisplay
import scalafx.scene.control.Label
import scalafx.scene.control.Menu
import scalafx.scene.control.MenuBar
import scalafx.scene.control.MenuItem
import scalafx.scene.control.TextField
import scalafx.scene.input.KeyCombination
import scalafx.scene.layout.HBox
import scalafx.scene.layout.VBox
import scalafx.scene.paint.Color
import scalafx.scene.paint.Color.sfxColor2jfx
import scalafx.scene.text.Font
import scalafx.scene.text.Text
import scalafx.scene.text.TextAlignment
import scalafx.stage.FileChooser
import scalafx.stage.FileChooser.ExtensionFilter
import scalafx.stage.Stage

import Messages._
import RxMessage.Implicits._

object Resources {
  private def res(path: String): URL = this.getClass().getResource(path)
  private def resExt(path: String): String = res(path).toExternalForm()
  
  val fontawesome: Font = Font.loadFont(resExt("fontawesome-webfont.ttf"), 20)
}

object Config {
  val defaultBgColor = Color.CornflowerBlue
  val defaultFps = 12.0d
}

class Widgets(implicit stage: Stage, system: ActorSystem) {
  
  val helpText = new Label {
    id = "helpText"
    text = "Ctrl+O to open a Photoshop file\nas an animated frame set."
    textAlignment = TextAlignment.Center
    
    animationLoaded.subscribe(_ => runLater { visible = false })
  }
  
  
  val fileManager = new FileManager()
  
  
  val fileChooser = new FileChooser {
    title = "Open PSD File"
    extensionFilters += new ExtensionFilter("PSD Files", "*.psd")
  
    def openPsd(): Unit = 
      Option(showOpenDialog(stage)) foreach { file =>
        // Remember directory for next time.
        initialDirectory = file.getParentFile
        // Then message the file for loading.
        Messages.file.publish(file)
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
    
    animationPaused .subscribe(_ => runLater { disable = true })
    animationPlaying.subscribe(_ => runLater { disable = false; requestFocus() })
    
    onAction = () => Messages.pause.publish()
  }
  
  
  val playButton = new FontAwesomeButton("Play", "\uf04b", "text-lg") {
    id = "play"
    disable = true
    
    prefHeight = 60
    prefWidth  = 60
    
    animationPlaying.subscribe(_ => runLater { disable = true })
    animationPaused .subscribe(_ => runLater { disable = false; requestFocus() })
    
    onAction = () => Messages.play.publish()
  }
  
  
  val fpsLabel = new Label("FPS") {
    rotate = -90
  }
  
  val fpsInput = new TextField {
    text = "%.0f".format(Config.defaultFps)
    prefColumnCount = 2
    prefHeight = 44
    prefWidth = 44
    
    val fpsFormat = "^([1-9][0-9]?)$".r
    
    def fps: Int = fpsFormat.findFirstIn(text.value).map(_.toInt).getOrElse(Config.defaultFps.toInt)
    
    text.onChange((obsval, prev, now) => now match {
      case fpsFormat(x) => Messages.fps.publish(now.toDouble)
      case _            => text = prev
    })
    
    fpsUp  .subscribe(_ => runLater { text = (fps + 1).toString })
    fpsDown.subscribe(_ => runLater { text = (fps - 1).toString })
  }
  
  val fpsArrows = new VBox {
    this.spacing = 2
    this.alignment = Pos.Center
    this.children = Seq(
      new FontAwesomeButton("Up", "\uf0d8", "text-sm") {
        this.onAction = () => fpsUp.publish()
      },
      new FontAwesomeButton("Down", "\uf0d7", "text-sm") {
        this.onAction = () => fpsDown.publish()
      })
  }
  
  val fpsControl = new HBox {
    styleClass add "fps-control"
    alignment = Pos.CenterLeft
    children = Seq(
      fpsLabel,
      fpsInput,
      fpsArrows.withMargin(Left(2)))
  }
  
  
  val colorPicker = new ColorPicker {
    value = Config.defaultBgColor
    visible = false
    prefWidth = 0
    prefHeight = 0
    maxWidth = 0
    maxHeight = 0
    
    onAction = () => { bgColor.publish(value.value) }
  }
  
  
  val canvas = new FrameCanvas {
    import FrameCanvasFsm._
    
    visible = false
    
    bgColor.subscribe { setBgColor(_) }
    
    val limit = Duration(500, MILLISECONDS) // implicits not finding this... *sigh*
    
    fps.observable.throttleLast(limit).forwardTo(fsm, SetFps(_))
    frames.forwardTo(fsm, SetFrames(_))
    play  .forwardTo(fsm, Play)
    pause .forwardTo(fsm, Pause)
    
    def playing() = animationPlaying.publish()
    def paused() = animationPaused.publish()
    def loaded() = animationLoaded.publish()
  }
  
  
  class FontAwesomeButton(name: String, fontAwesomeIcon: String, iconClass: String) extends Button(name) {
    alignment = Pos.Center
    contentDisplay = ContentDisplay.GraphicOnly
    graphic = new Text(fontAwesomeIcon) {
      styleClass add iconClass
      font = Resources.fontawesome
    }
  }
}
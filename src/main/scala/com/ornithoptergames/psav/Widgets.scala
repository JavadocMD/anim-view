package com.ornithoptergames.psav

import java.net.URL
import scala.concurrent.duration.Duration
import scala.concurrent.duration.MILLISECONDS
import FrameCanvasFsm._
import Messages._
import RxMessage.Implicits.ActorForwardable
import akka.actor.ActorSystem
import scalafx.Includes._
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
import scalafx.scene.control.ProgressIndicator
import scalafx.scene.control.TextField
import scalafx.scene.image.Image
import scalafx.scene.input.KeyCombination
import scalafx.scene.layout.HBox
import scalafx.scene.layout.VBox
import scalafx.scene.paint.Color
import scalafx.scene.paint.Color._
import scalafx.scene.text.Font
import scalafx.scene.text.Text
import scalafx.scene.text.TextAlignment
import scalafx.stage.FileChooser
import scalafx.stage.FileChooser.ExtensionFilter
import scalafx.stage.Stage
import scalafx.scene.layout.Priority
import com.ornithoptergames.psav.RxMessage.Impulse
import com.ornithoptergames.psav.RxMessage.Implicits._

object Resources {
  private def res(path: String): URL = this.getClass().getResource(path)
  private def resExt(path: String): String = res(path).toExternalForm()
  
  
  val fontawesome32: Font = Font.loadFont(resExt("fontawesome-webfont.ttf"), 32)
  val fontawesome20: Font = Font.loadFont(resExt("fontawesome-webfont.ttf"), 20)
  val fontawesome12: Font = Font.loadFont(resExt("fontawesome-webfont.ttf"), 12)
  
  // JavaFX appears to ignore different sizes of icons and just uses the last one, so whatever.
  val icons: List[Image] = List(new Image(resExt("icon-64.png")))
}

object Config {
  val defaultBgColor = Color.CornflowerBlue
  val defaultFps = 12.0d
  val defaultFilters = List("^Background$".r, "^[-].*$".r)
}

class Widgets(implicit stage: Stage, system: ActorSystem) {
  import FontAwesomeButton._
  
  val helpText = new Label {
    id = "helpText"
    text = "Ctrl+O to open an image file\nas an animated frame set."
    textAlignment = TextAlignment.Center
    
    animationLoaded.subscribe(_ => runLater { visible = false })
  }
  
  
  val fileManager = new FileManager()
  
  
  val fileChooser = new FileChooser {
    title = "Open File"
    extensionFilters ++= Seq(
      new ExtensionFilter("All Files", "*"),
      new ExtensionFilter("PSD Files", "*.psd"),
      new ExtensionFilter("SVG Files", "*.svg"))
  
    def choose(): Unit = 
      Option(showOpenDialog(stage)) foreach { file =>
        // Remember directory for next time.
        initialDirectory = file.getParentFile
        // Then message the file for loading.
        Messages.newFile.onNext(file)
      }
  }
  
  
  val menu = new MenuBar {
    this.hgrow = Priority.Always
    
    this.menus = Seq(
      new Menu("_File") {
        this.mnemonicParsing = true
        
        this.items = Seq(
          new MenuItem("_Open...") {
            this.mnemonicParsing = true
            this.accelerator = KeyCombination("Ctrl+O")
            this.onAction = () => fileChooser.choose()
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
  
  
  val pauseButton = new FontAwesomeButton("Pause", "\uf04c", Large) {
    id = "pause"
    disable = true
    
    prefHeight = 60
    prefWidth  = 60
    
    animationPaused .subscribe(_ => runLater { disable = true })
    animationPlaying.subscribe(_ => runLater { disable = false; requestFocus() })
    
    onAction = () => Messages.pause.onNext(Impulse)
  }
  
  
  val playButton = new FontAwesomeButton("Play", "\uf04b", Large) {
    id = "play"
    disable = true
    
    prefHeight = 60
    prefWidth  = 60
    
    animationPlaying.subscribe(_ => runLater { disable = true })
    animationPaused .subscribe(_ => runLater { disable = false; requestFocus() })
    
    onAction = () => Messages.play.onNext(Impulse)
  }
  
  
  val fpsLabel = new Label("FPS") {
    rotate = -90
  }
  
  val fpsInput = new TextField {
    text = "%.0f".format(Config.defaultFps)
    prefColumnCount = 2
    prefHeight = 50
    prefWidth = 50
    
    val fpsFormat = "^([1-9][0-9]?)$".r
    
    def fps: Int = fpsFormat.findFirstIn(text.value).map(_.toInt).getOrElse(Config.defaultFps.toInt)
    
    text.onChange((obsval, prev, now) => now match {
      case fpsFormat(x) => Messages.fps.onNext(now.toDouble)
      case _            => text = prev
    })
    
    fpsUp  .subscribe(_ => runLater { text = (fps + 1).toString })
    fpsDown.subscribe(_ => runLater { text = (fps - 1).toString })
  }
  
  val fpsArrows = new VBox {
    this.spacing = 2
    this.alignment = Pos.Center
    this.children = Seq(
      new FontAwesomeButton("Up", "\uf0d8", Small) {
        this.onAction = () => fpsUp.onNext(Impulse)
      },
      new FontAwesomeButton("Down", "\uf0d7", Small) {
        this.onAction = () => fpsDown.onNext(Impulse)
      })
  }
  
  val fpsControl = new HBox {
    styleClass add "fps-control"
    alignment = Pos.CenterLeft
    children = Seq(
      fpsLabel,
      fpsInput,
      fpsArrows.withMargin(LeftInset(2)))
  }
  
  
  val colorPicker = new ColorPicker {
    value = Config.defaultBgColor
    visible = false
    forceSize(this, 0, 0)
    
    onAction = () => { bgColor.onNext(value.value) }
  }
  
  
  val loading = new ProgressIndicator {
    progress = ProgressIndicator.INDETERMINATE_PROGRESS
    visible = false
    forceSize(this, 25, 25)
    margin = Insets(5)
    alignmentInParent = Pos.TopRight
    
    // Show when loading a file, hide when animation is loaded.
    val show = (newFile merge updateFile).map(_ => true)
    val hide = animationLoaded.map(_ => false)
    (show merge hide).subscribe(v => runLater { visible = v })
  }
  
  
  val canvas = new FrameCanvas {
    import FrameCanvasFsm._
    
    visible = false
    
    // Configure event handling and emitting on this instance.
    bgColor.subscribe { setBgColor(_) }
    
    val limit = Duration(500, MILLISECONDS) // implicits not finding this... *sigh*
    fps.debounce(limit).forwardTo(fsm, SetFps(_))
    
    newFrames.forwardTo(fsm, NewFrames(_))
    updateFrames.forwardTo(fsm, UpdateFrames(_))
    
    play.forwardTo(fsm, Play)
    pause.forwardTo(fsm, Pause)
    
    def playing() = animationPlaying.onNext(Impulse)
    def paused() = animationPaused.onNext(Impulse)
    def loaded() = animationLoaded.onNext(Impulse)
  }
  
  object FontAwesomeButton {
    trait FontSize { def font: Font }
    case object Large extends FontSize { val font = Resources.fontawesome32 }
    case object Medium extends FontSize { val font = Resources.fontawesome20 }
    case object Small extends FontSize { val font = Resources.fontawesome12 }
  }
  class FontAwesomeButton(name: String, fontAwesomeIcon: String, iconSize: FontSize) extends Button(name) {
    alignment = Pos.Center
    contentDisplay = ContentDisplay.GraphicOnly
    graphic = new Text(fontAwesomeIcon) {
      font = iconSize.font
    }
  }
}
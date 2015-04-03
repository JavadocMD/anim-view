# Animation Viewer

A free, open-source tool to streamline your 2D sprite animation workflow in Photoshop.

Check out the intro video here: http://youtu.be/KAiKDr-uafo

## Download

Downloads are available in the [releases section](../../releases).

If you have a Java 7 Runtime installed, choose a "java7" download. Simply unpack it someplace convenient and run `anim-view-<version>.jar`

If you are on Windows and either don't want to install Java 7 *or* you have a different Java runtime installed, choose the "win" download. It comes with a bundled Java 7 that you will not need to install. Simply unzip someplace convenient and run `anim-view-<version>.exe`

## Usage

Animation Viewer is designed to work with Photoshop files that are organized in a certain way.

* Each animation frame should be a separate layer that overlaps directly with the layers under it.
* Frames should be ordered from bottom to top. The first frame will be on bottom and the last frame on top.
* Any layer named "Background" or that starts with a dash (-) will be ignored. This way you can have a palette or other special-use layers that are not drawn into the animation.

Open a PSD file (File -> Open, or Ctrl+O) and playback will begin immediately. That PSD file will now be watched for changes. If you make an edit and save your PSD file, Animation Viewer will automatically load your changes and begin displaying them.

You can change the background color that your animation is drawn onto (Edit -> Set Background Color, or Ctrl+B). Note that you will only see this color if your frames contain transparent pixels.

The Pause and Play buttons will pause and continue playback. If you have paused the animation, edits to your PSD file will not be immediately visible, but you will see them when you hit play again.

The FPS setting changes how quickly your frames are played. Values can range from 1 to 99. Integer values only.

## Thanks!

I hope you'll find this tool useful as you develop animations. Feel free to get in touch with Ornithopter Games on [Twitter](http://twitter.com/OrnithopterGame) or at [the website](http://ornithoptergames.com/) if you have ideas on how Animation Viewer can be improved, or even if you just want to show off your work!

Also thank you to these libraries without whose work Animation Viewer would have been impossible (or at least really, really hard.)

* [haraldk/TwelveMonkeys](//github.com/haraldk/TwelveMonkeys)
* [lloydmeta/schwatcher](//github.com/lloydmeta/schwatcher)
* [ReactiveX/RxScala](//github.com/ReactiveX/RxScala)

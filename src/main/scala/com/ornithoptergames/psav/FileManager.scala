package com.ornithoptergames.psav

import java.io.File
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY

import scala.util.matching.Regex

import com.beachape.filemanagement.RxMonitor
import com.ornithoptergames.psav.Messages.file
import com.ornithoptergames.psav.Messages.frameFilter

import akka.actor.ActorSystem

class FileManager(implicit system: ActorSystem) {
  
  // fileWatcher will monitor a path and emit the file when it's modified.
  val fileWatcher = RxMonitor()
  
  // Whenever we see a *new* file, change fileWatcher's watching path.
  file.observable.distinctUntilChanged.subscribe { file =>
    // Registration is "bossy" by default here: don't have to worry about unregistering old paths.
    fileWatcher.registerPath(ENTRY_MODIFY, file.toPath())
  }
  
  // Whenever fileWatcher sees a reload, re-publish the file.
  fileWatcher.observable.subscribe { event => file.publish(event.path.toFile()) }
  
  /* And whenever a file is published or the frame-name exclude list changes, 
   * load & publish the frames and publish the play message. */
  val publishFrames: Function1[(File, List[Regex]), Unit] = {
    case ((file, filters)) => {
      val exclude = (f: Frame) => filters.exists { _.pattern.matcher(f.name).matches() }
      
      PsdLoader.loadPsd(file).foreach { info =>
        val filteredFrames = info.frames.filterNot(exclude)
        val filteredInfo = FrameInfo(info.size, filteredFrames)
        Messages.frames.publish(filteredInfo)
        Messages.play.publish()
      }
    }
  }
  
  file.observable.combineLatest(frameFilter.observable).subscribe(publishFrames)
}
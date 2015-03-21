package com.ornithoptergames.psav

import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY

import com.beachape.filemanagement.RxMonitor
import com.ornithoptergames.psav.Messages.file

import akka.actor.ActorSystem

class FileManager(implicit system: ActorSystem) {
  
  // fileWatcher will monitor a path and emit the file when it's modified.
  val fileWatcher = RxMonitor()
  
  // Whenever we see a *new* file, change fileWatcher's watching path.
  file.observable.distinctUntilChanged.subscribe { file =>
    fileWatcher.registerPath(ENTRY_MODIFY, file.toPath())
  }
  
  // Whenever fileWatcher sees a reload, re-publish the file.
  fileWatcher.observable.subscribe { event => file.publish(event.path.toFile()) }
  
  // And whenever a file is published, load & publish the frames and publish the play message.
  file.observable.subscribe { file =>
    PsdLoader.loadPsd(file).foreach { frames => 
      Messages.frames.publish(frames)
      Messages.play.publish()
    }
  }
}
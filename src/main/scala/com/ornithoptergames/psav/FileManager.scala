package com.ornithoptergames.psav

import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import com.beachape.filemanagement.RxMonitor
import com.ornithoptergames.psav.FrameInfoLoader._
import com.ornithoptergames.psav.Messages._
import com.ornithoptergames.psav.RxMessage.Implicits._

import akka.actor.ActorSystem
import akka.util.Timeout

class FileManager(implicit system: ActorSystem) {
  
  // fileWatcher will monitor a path and emit the file when it's modified.
  val fileWatcher = RxMonitor()
  
  // When we see a new file, change fileWatcher's watching path.
  newFile.observable.subscribe { file =>
    // Registration is "bossy" by default here: don't have to worry about unregistering old paths.
    fileWatcher.registerPath(ENTRY_MODIFY, file.toPath())
  }
  
  // When fileWatcher sees a reload, publish an updated file.
  fileWatcher.observable.subscribe { event => updateFile.publish(event.path.toFile()) }
  
  implicit val timeout = Timeout(15 seconds)
  val loader = system.actorSelection(system / FrameInfoLoader.actorName)
  
  // When a file is updated or the frame-name exclude list changes, publish updated frames.
  updateFile.observable.combineLatest(frameFilter.observable).map(Load.tupled)
    .pipeThrough(loader, updateFrames, { case t => t.printStackTrace() })
  
  // When a new file is selected, publish new frames.
  val mostRecentFrameFilter = new MostRecent(frameFilter.subject)
  newFile.observable.map(f => Load(f, mostRecentFrameFilter.get))
    .pipeThrough(loader, newFrames, { case t => t.printStackTrace() })
}
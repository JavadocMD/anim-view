package com.ornithoptergames.psav

import java.io.File
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY

import scala.util.matching.Regex

import com.beachape.filemanagement.RxMonitor
import com.ornithoptergames.psav.Messages._

import akka.actor.ActorSystem

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
  
  
  def publishFrames(publishTo: RxMessage[FrameInfo]): Function1[(File, List[Regex]), Unit] = {
    case ((file, filters)) => {
      val exclude = (f: Frame) => filters.exists { _.pattern.matcher(f.name).matches() }
      
      PsdLoader.loadPsd(file).foreach { info =>
        val filteredFrames = info.frames.filterNot(exclude)
        val filteredInfo = FrameInfo(info.size, filteredFrames)
        publishTo.publish(filteredInfo)
      }
    }
  }
  
  // When a file is updated or the frame-name exclude list changes, publish updated frames.
  updateFile.observable.combineLatest(frameFilter.observable).subscribe(publishFrames(Messages.updateFrames))
  
  // When a new file is selected, publish new frames.
  val mostRecentFrameFilter = new MostRecent(frameFilter.subject)
  newFile.observable.map(f => (f, mostRecentFrameFilter.get)).subscribe(publishFrames(Messages.newFrames))
}
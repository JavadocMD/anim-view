package com.ornithoptergames.psav

import akka.actor.Actor
import com.ornithoptergames.psav.Messages._
import akka.actor.ActorLogging
import com.beachape.filemanagement.MonitorActor
import akka.actor.Props
import com.beachape.filemanagement.Messages.UnRegisterCallback
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.Path
import com.beachape.filemanagement.RegistryTypes.Callback
import com.beachape.filemanagement.Messages.RegisterCallback

class FileManager(msg: Messaging) extends Actor with ActorLogging {

  val fileMonitorProps = Props(classOf[MonitorActor], 5)
  val fileMonitor = context.actorOf(fileMonitorProps)

  private[this] var watchedPath: Option[Path] = None

  def receive = {
    case LoadFile(file) => PsdLoader.loadPsd(file).foreach { fi =>
      // stop watching old file
      watchedPath.foreach { p => fileMonitor ! UnRegisterCallback(ENTRY_MODIFY, false, p) }

      val newPath = file.toPath()
      
      // start watching new file
      fileMonitor ! RegisterCallback(
          ENTRY_MODIFY,
          None,
          false,
          newPath,
          onModify)

      watchedPath = Some(newPath)
      
      msg.publish(SetFrames(fi), self)
      msg.publish(Play, self)
    }
  }

  val onModify: Callback = (path: Path) =>
    PsdLoader.loadPsd(path.toFile()).foreach { fi => 
      msg.publish(SetFrames(fi))
      msg.publish(Play, self)
    }

  msg.subscribe(file_?, self)
}
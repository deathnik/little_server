package little_server

import java.io.File

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.{Config, ConfigFactory}

object GlobalConfig {
  def getFile(name: String): String = {
    this.getClass.getResource("/" + name).getFile
  }

  def load_config(name: String): Config = {
    ConfigFactory.parseFile(new File(name))
  }

  //loads part of "War and Peace" novel by Leo Tolstoy
  object TolstoyStorage {
    val text = scala.io.Source.fromFile("voina.txt", "UTF-8").mkString
    val len = text.length
  }

  final val header_start: String = "HTTP/1.1 200 OK\r\n" +
    "Server: DeathNikServer\r\n" +
    "Content-Type: text/html\r\n" +
    "Content-Length: "
  final val header_end: String = "\r\n" +
    "Connection: close\r\n\r\n"

  final val msg = header_start + TolstoyStorage.len + header_end + TolstoyStorage.text
  final val msg_parts = msg.sliding(1024, 1024).toArray
  final val msg_parts_len = msg_parts.length

  final val system = ActorSystem("ServerSystem", load_config("dispatcher-application.conf"))

  var allowPrintingStats = true
  var counter: ActorRef = null


  var current_active = 0
  var max_active = 0
}
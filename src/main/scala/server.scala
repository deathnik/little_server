import java.io.PrintStream
import java.net.{Socket, ServerSocket}
import java.util.Date

import akka.actor.{Actor, ActorSystem, Props}

object Main {
  def main(args: Array[String]) {
    startHttpServer(8091)
  }

  def startHttpServer(port: Int) = {
    try {
      val system = ActorSystem("BasicServerSystem")
      val requestActor = system.actorOf(Props[HttpRequestActor], name = "requestActor")
      val server = new ServerSocket(port)
      println(s"BasicServer listening on port $port")
      while (true) {
        val socket = server.accept()
        requestActor ! socket
      }
    } catch {
      case e: Exception => println(e.getMessage)
    }
  }
}

class HttpRequestActor extends Actor {

  override def receive: Receive = {
    case sock: Socket =>
      try {
        val requestId = java.util.UUID.randomUUID.toString
        val responseActor = context.actorOf(Props[HttpResponseActor], name = s"responseActor$requestId")
        responseActor ! sock
      } catch {
        case e: Exception => println(e.getMessage)
      }
  }
}

class HttpResponseActor extends Actor {
  final val header_start: String = "HTTP/1.1 200 OK\r\n" +
    "Server: DeathNikServer\r\n" +
    "Content-Type: text/html\r\n" +
    "Content-Length: "
  final val header_end: String = "\r\n" +
    "Connection: close\r\n\r\n";

  override def receive: Actor.Receive = {
    case sock: Socket =>
      try {
        val out = new PrintStream(sock.getOutputStream)
        val date = new Date()
        val msg = "Received on " + date
        val msg_len = msg.length
        out.println(s"$header_start$msg_len$header_end$msg")
        out.close()
      } catch {
        case e: Exception => println(e.getMessage)
      } finally {
        sock.close()
      }
  }
}

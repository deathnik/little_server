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

  override def receive: Actor.Receive = {
    case sock: Socket =>
      try {
        val out = new PrintStream(sock.getOutputStream)
        val date = new Date()
        out.println("Received on " + date)
        out.close()
      } catch {
        case e: Exception => println(e.getMessage)
      } finally {
        sock.close()
      }
  }
}

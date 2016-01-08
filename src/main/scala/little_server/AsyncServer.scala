package little_server

import java.net.{InetAddress, InetSocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel, SocketChannel}
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props}

import scala.concurrent.duration.Duration

//implicit
import scala.concurrent.ExecutionContext.Implicits.global

object AsyncServer {
  def apply(): AsyncServer = {
    new AsyncServer
  }
}

class AsyncServer {
  var accept_selector: Selector = null
  var response_selectors: IndexedSeq[Selector] = null
  var registered_selectors_cnt: Int = 0
  var next_selector = 0

  def move_to_next_selector {
    next_selector = (next_selector + 1) % registered_selectors_cnt
  }

  def process_requests() {
    val iterator = accept_selector.selectedKeys().iterator
    while (iterator.hasNext) {
      val key: SelectionKey = iterator.next
      iterator.remove()

      if (key.isAcceptable) {
        val ssChannel: ServerSocketChannel = key.channel().asInstanceOf[ServerSocketChannel]
        val sChannel: SocketChannel = ssChannel.accept()
        sChannel.configureBlocking(false)
        sChannel.register(response_selectors(next_selector), SelectionKey.OP_READ)
        move_to_next_selector
      }
    }
  }


  def start(port: Int) {
    try {
      registered_selectors_cnt = 10

      def create_selector(i: Int): Selector = {
        val selector = Selector.open()
        selector
      }

      // prepare selectors and response actors
      response_selectors = for (i <- 1 to registered_selectors_cnt) yield create_selector(i)
      for (i <- 0 to registered_selectors_cnt - 1) {
        val act = GlobalConfig.system.actorOf(Props[AsyncHttpResponseActor], name = "little_server.AsyncHttpResponseActor" + i)
        act ! response_selectors(i)
      }

      //open server socket
      val hostIPAddress: InetAddress = InetAddress.getByName("localhost")
      accept_selector = Selector.open
      val ssChannel: ServerSocketChannel = ServerSocketChannel.open
      ssChannel.configureBlocking(false)
      ssChannel.socket.bind(new InetSocketAddress(hostIPAddress, port))
      ssChannel.register(accept_selector, SelectionKey.OP_ACCEPT)

      //main loop
      println(s"AsyncBasicServer listening on port $port")
      while (true) {
        if (accept_selector.select > 0) {
          process_requests
        }
      }
    } catch {
      case e: Exception =>
        println("Exception in server main, exiting:")
        println(e.getMessage)
    }
  }
}


class AsyncHttpResponseActor extends Actor {
  var sel: Selector = null

  case object AnswerRequests

  override def receive: Receive = {
    case _sel: Selector =>
      sel = _sel
      self ! AnswerRequests

    case AnswerRequests =>
      var loop = true
      while (loop) {
        if (sel.select(100) <= 0) {
          loop = false
        } else {
          process_requests(sel)
        }
      }

      // here we unblock selector, allowing new connections to be registered
      GlobalConfig.system.scheduler.scheduleOnce(Duration.create(50, TimeUnit.MILLISECONDS), self, AnswerRequests)
  }

  def process_requests(sel: Selector): Unit = {
    val iterator = sel.selectedKeys().iterator
    while (iterator.hasNext) {
      val key: SelectionKey = iterator.next
      iterator.remove()

      if (key.isReadable) {
        val sChannel: SocketChannel = key.channel.asInstanceOf[SocketChannel]
        val buffer: ByteBuffer = ByteBuffer.wrap(GlobalConfig.msg.getBytes)
        sChannel.write(buffer)
        sChannel.close()
      }
    }
  }
}
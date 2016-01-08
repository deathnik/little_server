package little_server

object Main {
  def main(args:Array[String]){
    AsyncServer().start(8091)

    //SyncServer().startHttpServer(8091)
  }
}

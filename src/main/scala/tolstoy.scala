object TolstoyStorage {
  //print(new java.io.File(".").getCanonicalPath)
  val text = scala.io.Source.fromFile("voina.txt", "UTF-8").mkString
  val len = text.length
}

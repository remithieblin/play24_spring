package provider

class MyProvider extends Provider {

  override def create(): Unit = {
    println("lol in provider")
  }

}

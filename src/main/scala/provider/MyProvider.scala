package provider

import javax.inject.Named

@Named
class MyProvider extends Provider {

  override def create(): Unit = {
    println("lol in provider")
  }

}

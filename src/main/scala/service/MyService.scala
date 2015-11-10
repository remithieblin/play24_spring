package service

import javax.inject.Named

@Named
class MyService extends Service {

  override def create: Unit = {
    println("lol")
  }
}

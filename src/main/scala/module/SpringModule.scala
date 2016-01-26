package module

import com.google.inject.AbstractModule
import loader.Global

class SpringModule extends AbstractModule {

  println("lol SpringModule")

  override def configure(): Unit = {
    bind(classOf[Global]).asEagerSingleton()
  }
}

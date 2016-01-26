package module

import com.google.inject.AbstractModule
import provider.{MyProvider, Provider}

class MyModule extends AbstractModule{

  override def configure(): Unit = {
    bind(classOf[Provider])
      .to(classOf[MyProvider])
  }
}

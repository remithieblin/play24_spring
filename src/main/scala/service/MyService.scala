package service

import javax.inject.{Inject, Named}

import provider.Provider

//@Named
class MyService @Inject() (provider: Provider) extends Service {

  override def create(): Unit = {
    provider.create()
  }
}

package com.demo.spring

import play.api.{Environment, Configuration}

class MyCode {
  println("In my code")
}

class MyModule extends play.api.inject.Module {
  def bindings(environment: Environment, configuration: Configuration) = {
    Seq(bind[MyCode].toInstance(new MyCode))
  }
}

package com.demo.spring.service

import javax.inject.{Inject, Named}

import com.demo.spring.provider.Provider

@Named
class MyService @Inject() (provider: Provider) extends Service {

  override def create(): Unit = {

    println("In service")
    provider.create()
  }
}

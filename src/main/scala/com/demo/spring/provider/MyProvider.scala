package com.demo.spring.provider

import javax.inject.{Inject, Named}

import com.demo.spring.MyCode

@Named
class MyProvider @Inject() (myCode: MyCode) extends Provider {

  override def create(): Unit = {
    println("In provider")
  }

}

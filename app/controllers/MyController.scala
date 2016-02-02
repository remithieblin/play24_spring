package controllers

import javax.inject.Inject

import play.api.mvc._
import service.MyService

//@Inject() (service: MyService)
class MyController  extends Controller {

  def get = Action { request =>
//    service.create

    println("lol controller")

    Ok
  }

}

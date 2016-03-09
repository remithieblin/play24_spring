package controllers

import javax.inject.{Named, Inject}

import play.api.mvc._
import service.MyService

//@Inject() (service: MyService)
@Named
class MyController  extends Controller {

  def get = Action { request =>
//    service.create

    println("lol controller")

    Ok
  }

}

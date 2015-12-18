package controllers

import javax.inject.Inject

import play.api.mvc._
import service.MyService

class MyController  (service: MyService) extends Controller {

  //@Inject()

  def get = Action { request =>
    service.create
    Ok
  }

}

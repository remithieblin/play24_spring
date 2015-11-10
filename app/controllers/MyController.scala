package controllers

import javax.inject.Inject

import play.api.mvc._
import service.MyService

class MyController @Inject() (service: MyService) extends Controller {

  def get = Action { request =>
    service.create
    Ok
  }

}

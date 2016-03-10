package controllers

import javax.inject.{Inject, Named}

import play.api.mvc._
import service.Service


@Named
class MyController @Inject() (service: Service) extends Controller {

  def get = Action { request =>
    service.create()

    println("lol controller")

    Ok
  }

}

package config

import javax.inject.{Provider, Singleton, Inject}

import org.springframework.context.annotation.{Bean, ComponentScan, Configuration}
import play.api.http.HttpConfiguration
import play.api.inject.Injector
import play.api.routing.Router
import play.api._

@Configuration
@ComponentScan(Array("provider", "routes"))
class AppConfig  {

//  println("lol AppConfig")

//  @Bean
//  @Inject()
//  def globalPlugin (app: Application) : GlobalPlugin = new GlobalPlugin(app)

//  def router(): Router =

//  @Singleton
//  @Inject()
//  def router (injector: Injector, environment: Environment, configuration: play.api.Configuration, httpConfig: HttpConfiguration) = {
//
//      val prefix = httpConfig.context
//
//      val router = Router.load(environment, configuration)
//        .fold[Router](Router.empty)(injector.instanceOf(_))
//      router.withPrefix(prefix)
//
//  }
//
//  @Singleton
//  @Inject()
//  def plugin (environment: Environment, injector: Injector) = {
//    Plugins(environment, injector)
//  }
}

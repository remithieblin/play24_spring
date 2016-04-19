package play.api.inject
package spring

import javax.inject.{Singleton, Named, Inject, Provider}

import org.specs2.mutable.Specification
import play.api.{Configuration, Environment}
import play.api.inject.{ConfigurationProvider, Injector, Module}

class SpringApplicationBuilderSpec extends Specification {

  "GuiceApplicationBuilder" should {

    "add bindings" in {
      val injector = new SpringApplicationBuilder()
        .in(Environment.simple())
        .bindings(Seq(
          new AModule,
          new Module {
            override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
              Seq(bind[B].to[B1])
            }
          }
          ))
        .injector()

      injector.instanceOf[A] must beAnInstanceOf[A1]
      injector.instanceOf[B] must beAnInstanceOf[B1]
    }
  }

}

trait A
@Named
class A1 extends A
@Named
class A2 extends A

class AModule extends Module {
  def bindings(env: Environment, conf: Configuration) = Seq(
    bind[A].to[A1]
  )
}

trait B
@Named
class B1 extends B

class ExtendConfiguration(conf: (String, Any)*) extends Provider[Configuration] {
  @Inject
  var injector: Injector = _
  lazy val get = {
    val current = injector.instanceOf[ConfigurationProvider].get
    current ++ Configuration.from(conf.toMap)
  }
}

trait C

//@Named
@Singleton
class C1 extends C {
  throw new EagerlyLoadedException
}

class EagerlyLoadedException extends RuntimeException
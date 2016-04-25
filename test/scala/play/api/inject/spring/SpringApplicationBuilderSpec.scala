package play.api.inject
package spring

import javax.inject.{Singleton, Named, Inject, Provider}

import org.specs2.mutable.Specification
import play.api.{Environment, Configuration}
import play.api.inject.{ConfigurationProvider, Injector, Module}

class SpringApplicationBuilderSpec extends Specification {

  "SpringApplicationBuilder" should {

    "add bindings" in {
      val injector = new SpringApplicationBuilder()
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

    "override bindings" in {


      val app = new SpringApplicationBuilder()
        .bindings(Seq(new AModule))
        .overrides(
          Seq(
            new Module {
              def bindings(environment: Environment, configuration: Configuration) = Seq(
                bind[Configuration] to new ExtendConfiguration("a" -> 1),
                bind[A].to[A2])}))
        .scanning(DefaultPlayModuleBeanDefinitionReader.defaultPackages())
        .build()

      app.configuration.getInt("a") must beSome(1)
      app.injector.instanceOf[A] must beAnInstanceOf[A2]
    }
//
//    "disable modules" in {
//      val injector = new SpringApplicationBuilder()
//        .bindings(Seq(new AModule))
//        .disable[play.api.i18n.I18nModule]
//        .disable(classOf[AModule])
//        .injector
//
//      injector.instanceOf[play.api.i18n.Langs] must throwA[com.google.inject.ConfigurationException]
//      injector.instanceOf[A] must throwA[com.google.inject.ConfigurationException]
//    }
//
//    "set initial configuration loader" in {
//      val extraConfig = Configuration("a" -> 1)
//      val app = new SpringApplicationBuilder()
//        .loadConfig(env => Configuration.load(env) ++ extraConfig)
//        .build
//
//      app.configuration.getInt("a") must beSome(1)
//    }
//
//    "set module loader" in {
//      val injector = new SpringApplicationBuilder()
//        .load((env, conf) => Seq(new BuiltinModule, bind[A].to[A1]))
//        .injector
//
//      injector.instanceOf[A] must beAnInstanceOf[A1]
//    }
//
//    "set loaded modules directly" in {
//      val injector = new SpringApplicationBuilder()
//        .load(new BuiltinModule, bind[A].to[A1])
//        .injector
//
//      injector.instanceOf[A] must beAnInstanceOf[A1]
//    }
//
//    "eagerly load singletons" in {
//      new SpringApplicationBuilder()
//        .load(new BuiltinModule, bind[C].to[C1])
//        .eagerlyLoaded()
//        .injector() must throwAn[CreationException]
//    }
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
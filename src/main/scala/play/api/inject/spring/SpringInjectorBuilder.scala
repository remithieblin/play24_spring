package play.api.inject.spring

import play.api.{PlayException, Configuration, Environment}
import play.api.inject._

class SpringInjectorBuilder {

}

/**
 * Magnet pattern for creating Guice modules from Play modules or bindings.
 */
trait SpringableModule {
  def springed(env: Environment, conf: Configuration): Seq[Class[_]]
  def disable(classes: Seq[Class[_]]): SpringableModule
}

/**
 * Loading and converting Guice modules.
 */
object SpringableModule extends SpringableModuleConversions {

  def loadModules(environment: Environment, configuration: Configuration): Seq[SpringableModule] = {
    Modules.locate(environment, configuration) map springable
  }

  /**
   * Attempt to convert a module of unknown type to a GuiceableModule.
   */
  def springable(module: Any): SpringableModule = module match {
    case playModule: Module => fromPlayModule(playModule)
    case annotatedClass: Class[_] => fromSpringClass(annotatedClass)
    case unknown => throw new PlayException(
      "Unknown module type",
      s"Module [$unknown] is not a Play module or a Guice module"
    )
  }

  /**
   * Apply GuiceableModules to create Guice modules.
   */
  def springed(env: Environment, conf: Configuration)(builders: Seq[SpringableModule]): Seq[Class[_]] =
    builders flatMap { module => module.springed(env, conf) }

}

/**
 * Implicit conversions to SpringableModules.
 */
trait SpringableModuleConversions {

  import scala.language.implicitConversions

  implicit def fromSpringClass(annotatedClass: Class[_]): SpringableModule = fromSpringClasses(Seq(annotatedClass))

  implicit def fromSpringClasses(annotatedClasses: Seq[Class[_]]): SpringableModule = new SpringableModule {
    def springed(env: Environment, conf: Configuration): Seq[Class[_]] = annotatedClasses
    def disable(classes: Seq[Class[_]]): SpringableModule = fromSpringClasses(filterOut(classes, annotatedClasses))
    override def toString = s"SpringableModule(${annotatedClasses.mkString(", ")})"
  }

  implicit def fromPlayModule(playModule: Module): SpringableModule = fromPlayModules(Seq(playModule))

  implicit def fromPlayModules(playModules: Seq[Module]): SpringableModule = new SpringableModule {
    def springed(env: Environment, conf: Configuration): Seq[Class[_]] = playModules.map(spring(env, conf))
    def disable(classes: Seq[Class[_]]): SpringableModule = fromPlayModules(filterOut(classes, playModules))
    override def toString = s"SpringableModule(${playModules.mkString(", ")})"
  }

  implicit def fromPlayBinding(binding: Binding[_]): SpringableModule = fromPlayBindings(Seq(binding))

  implicit def fromPlayBindings(bindings: Seq[Binding[_]]): SpringableModule = new SpringableModule {
    def springed(env: Environment, conf: Configuration): Seq[Class[_]] = Seq(spring(bindings))
    def disable(classes: Seq[Class[_]]): SpringableModule = this // no filtering
    override def toString = s"SpringableModule(${bindings.mkString(", ")})"
  }

  private def filterOut[A](classes: Seq[Class[_]], instances: Seq[A]): Seq[A] =
    instances.filterNot(o => classes.exists(_.isAssignableFrom(o.getClass)))

  /**
   * Convert the given Play module to a Guice module.
   */
  def spring(env: Environment, conf: Configuration)(module: Module): Class[_] =
    spring(module.bindings(env, conf))

  /**
   * Convert the given Play bindings to a Guice module.
   */
  def spring(bindings: Seq[Binding[_]]): Class[_] = {
    new com.google.inject.AbstractModule {
      def configure(): Unit = {
        for (b <- bindings) {
          val binding = b.asInstanceOf[PlayBinding[Any]]
          val builder = binder().withSource(binding).bind(GuiceKey(binding.key))
          binding.target.foreach {
            case ProviderTarget(provider) => builder.toProvider(GuiceProviders.guicify(provider))
            case ProviderConstructionTarget(provider) => builder.toProvider(provider)
            case ConstructionTarget(implementation) => builder.to(implementation)
            case BindingKeyTarget(key) => builder.to(GuiceKey(key))
          }
          (binding.scope, binding.eager) match {
            case (Some(scope), false) => builder.in(scope)
            case (None, true) => builder.asEagerSingleton()
            case (Some(scope), true) => throw new GuiceLoadException("A binding must either declare a scope or be eager: " + binding)
            case _ => // do nothing
          }
        }
      }
    }
  }

}
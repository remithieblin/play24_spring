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
    case annotatedClass: Class[_] => fromSpringModule(annotatedClass)
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
package play.api.inject.spring


import play.api.inject.{Modules, Module}
import play.api.{PlayException, Configuration, Environment}

class SpringInjectorBuilder {

}

object SpringableModule {

  def loadModules(environment: Environment, configuration: Configuration): Seq[Module] = {
    Modules.locate(environment, configuration) map springable
  }

  /**
   * Attempt to convert a module of unknown type to a GuiceableModule.
   */
  def springable(module: Any): Module = module match {
    case playModule: Module => playModule
    case unknown => throw new PlayException(
      "Unknown module type",
      s"Module [$unknown] is not a Play module"
    )
  }
}

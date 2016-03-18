package play.api.inject.spring

import play.api.routing.Router
import play.api.{ Application, Configuration, Environment, GlobalSettings, Logger, OptionalSourceMapper }
import play.api.inject.{ Injector => PlayInjector, RoutesProvider, bind }
import play.core.{ DefaultWebCommands, WebCommands }

/**
 * A builder for creating Applications using Spring.
 */
class SpringApplicationBuilder (
                                 environment: Environment = Environment.simple(),
                                 configuration: Configuration = Configuration.empty,
                                 modules: Seq[_] = Seq.empty,
                                 overrides: Seq[_] = Seq.empty,
                                 disabled: Seq[Class[_]] = Seq.empty,
                                 eagerly: Boolean = false,
                                 loadConfiguration: Environment => Configuration = Configuration.load,
                                 global: Option[GlobalSettings] = None
                                 )  extends SpringBuilder[SpringApplicationBuilder](
  environment, configuration, modules, overrides, disabled, eagerly
) {

  // extra constructor for creating from Java
  def this() = this(environment = Environment.simple())

  /**
   * Create a new Self for this immutable builder.
   * Provided by builder implementations.
   */
  override protected def newBuilder(environment: Environment,
                                    configuration: Configuration,
                                    modules: Seq[_], overrides: Seq[_],
                                    disabled: Seq[Class[_]],
                                    eagerly: Boolean): SpringApplicationBuilder = {
    copy(environment, configuration, modules, overrides, disabled, eagerly)
  }

  /**
   * Set the initial configuration loader.
   * Overrides the default or any previously configured values.
   */
  def loadConfig(loader: Environment => Configuration): SpringApplicationBuilder =
    copy(loadConfiguration = loader)

  /**
   * Set the initial configuration.
   * Overrides the default or any previously configured values.
   */
  def loadConfig(conf: Configuration): SpringApplicationBuilder =
    loadConfig(env => conf)

  /**
   * Internal copy method with defaults.
   */
  private def copy(
                    environment: Environment = environment,
                    configuration: Configuration = configuration,
                    modules: Seq[_] = modules,
                    overrides: Seq[_] = overrides,
                    disabled: Seq[Class[_]] = disabled,
                    eagerly: Boolean = eagerly,
                    loadConfiguration: Environment => Configuration = loadConfiguration,
                    global: Option[GlobalSettings] = global
                    ): SpringApplicationBuilder =
    new SpringApplicationBuilder(environment, configuration, modules, overrides, disabled, eagerly, loadConfiguration, global)
}

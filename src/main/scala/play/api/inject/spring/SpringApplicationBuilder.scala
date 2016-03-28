package play.api.inject.spring

import play.api.inject.{Injector => PlayInjector, _}
import play.api.routing.Router
import play.api.{ Application, Configuration, Environment, GlobalSettings, Logger, OptionalSourceMapper }
import play.core.{ DefaultWebCommands, WebCommands }

/**
 * A builder for creating Applications using Spring.
 */
class SpringApplicationBuilder (
                                 environment: Environment = Environment.simple(),
                                 configuration: Configuration = Configuration.empty,
                                 modules: Seq[Module] = Seq.empty,
                                 overrides: Seq[Module] = Seq.empty,
                                 disabled: Seq[Class[_]] = Seq.empty,
                                 eagerly: Boolean = false,
                                 loadConfiguration: Environment => Configuration = Configuration.load,
                                 global: Option[GlobalSettings] = None,
                                 loadModules: (Environment, Configuration) => Seq[Module] = SpringableModule.loadModules
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
                                    modules: Seq[Module], overrides: Seq[Module],
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
   * Create a new Play Application using this configured builder.
   */
  def build(): Application = injector().instanceOf[Application]

  /**
   * Create a new Play application Module for an Application using this configured builder.
   */
  override def applicationModule(): Seq[Module] = {
    val initialConfiguration = loadConfiguration(environment)
    val appConfiguration = initialConfiguration ++ configuration
    val globalSettings = global.getOrElse(GlobalSettings(appConfiguration, environment))

    // TODO: Logger should be application specific, and available via dependency injection.
    //       Creating multiple applications will stomp on the global logger configuration.
    Logger.configure(environment)

    if (appConfiguration.underlying.hasPath("logger")) {
      Logger.warn("Logger configuration in conf files is deprecated and has no effect. Use a logback configuration file instead.")
    }

    val loadedModules = loadModules(environment, appConfiguration)

    copy(configuration = appConfiguration)
      .bindings(loadedModules: Seq[Module])
      .bindings(
        Seq(new Module{
          def bindings(environment: Environment, configuration: Configuration) = Seq(
            bind[GlobalSettings] to globalSettings,
            bind[OptionalSourceMapper] to new OptionalSourceMapper(None),
            bind[WebCommands] to new DefaultWebCommands
          )
        })
      ).createModule()
  }

  /**
   * Internal copy method with defaults.
   */
  private def copy(
                    environment: Environment = environment,
                    configuration: Configuration = configuration,
                    modules: Seq[Module] = modules,
                    overrides: Seq[Module] = overrides,
                    disabled: Seq[Class[_]] = disabled,
                    eagerly: Boolean = eagerly,
                    loadConfiguration: Environment => Configuration = loadConfiguration,
                    global: Option[GlobalSettings] = global
                    ): SpringApplicationBuilder =
    new SpringApplicationBuilder(environment, configuration, modules, overrides, disabled, eagerly, loadConfiguration, global)
}

package play.api.inject.spring

import java.io.File

import loader.SpringInjector
import play.api.inject._
import play.api.inject.guice.{GuiceInjector, GuiceableModuleConversions, GuiceableModule}
import play.api.{PlayException, Mode, Configuration, Environment}
import play.api.inject.{ Binding => PlayBinding, BindingKey, Injector => PlayInjector, Module => PlayModule }

import scala.reflect.ClassTag

abstract class SpringBuilder[Self] protected (
                                               environment: Environment,
                                               configuration: Configuration,
                                               modules: Seq[SpringableModule],
                                               overrides: Seq[SpringableModule],
                                               disabled: Seq[Class[_]],
                                               eagerly: Boolean) {

  /**
   * Set the environment.
   */
  final def in(env: Environment): Self =
    copyBuilder(environment = env)

  /**
   * Set the environment path.
   */
  final def in(path: File): Self =
    copyBuilder(environment = environment.copy(rootPath = path))

  /**
   * Set the environment mode.
   */
  final def in(mode: Mode.Mode): Self =
    copyBuilder(environment = environment.copy(mode = mode))

  /**
   * Set the environment class loader.
   */
  final def in(classLoader: ClassLoader): Self =
    copyBuilder(environment = environment.copy(classLoader = classLoader))

  /**
   * Set the dependency initialization to eager.
   */
  final def eagerlyLoaded(): Self =
    copyBuilder(eagerly = true)

  /**
   * Add additional configuration.
   */
  final def configure(conf: Configuration): Self =
    copyBuilder(configuration = configuration ++ conf)

  /**
   * Add additional configuration.
   */
  final def configure(conf: Map[String, Any]): Self =
    configure(Configuration.from(conf))

  /**
   * Add additional configuration.
   */
  final def configure(conf: (String, Any)*): Self =
    configure(conf.toMap)

  /**
   * Add Guice modules, Play modules, or Play bindings.
   *
   * @see [[GuiceableModuleConversions]] for the automatically available implicit
   *      conversions to [[GuiceableModule]] from modules and bindings.
   */
  final def bindings(bindModules: SpringableModule*): Self =
    copyBuilder(modules = modules ++ bindModules)

  /**
   * Override bindings using Guice modules, Play modules, or Play bindings.
   *
   * @see [[GuiceableModuleConversions]] for the automatically available implicit
   *      conversions to [[GuiceableModule]] from modules and bindings.
   */
  final def overrides(overrideModules: SpringableModule*): Self =
    copyBuilder(overrides = overrides ++ overrideModules)

  /**
   * Disable modules by class.
   */
  final def disable(moduleClasses: Class[_]*): Self =
    copyBuilder(disabled = disabled ++ moduleClasses)

  /**
   * Disable module by class.
   */
  final def disable[T](implicit tag: ClassTag[T]): Self = disable(tag.runtimeClass)

  /**
   * Create a Play Injector backed by Guice using this configured builder.
   */
  def applicationModule(): SpringModule = createModule

  /**
   * Creation of the Guice Module used by the injector.
   * Libraries like Guiceberry and Jukito that want to handle injector creation may find this helpful.
   */
  def createModule(): SpringModule = {
    import scala.collection.JavaConverters._
    val injectorModule = SpringableModule.spring(Seq(
      bind[PlayInjector].to[SpringInjector],
      // Java API injector is bound here so that it's available in both
      // the default application loader and the Java Guice builders
      bind[play.inject.Injector].to[play.inject.DelegateInjector]
    ))
    val enabledModules = modules.map(_.disable(disabled))
    val bindingModules = SpringableModule.spring(environment, configuration)(enabledModules) :+ injectorModule
    val overrideModules = SpringableModule.springed(environment, configuration)(overrides)
    SpringModules.`override`(bindingModules.asJava).`with`(overrideModules.asJava)
  }

  /**
   * Create a Play Injector backed by Guice using this configured builder.
   */
  def injector(): PlayInjector = {
    try {
      val stage = environment.mode match {
        case Mode.Prod => Stage.PRODUCTION
        case _ if eagerly => Stage.PRODUCTION
        case _ => Stage.DEVELOPMENT
      }
      val springInjector = Spring.createInjector(stage, applicationModule())
      springInjector.getInstance(classOf[PlayInjector])
    } catch {
      case e: CreationException => e.getCause match {
        case p: PlayException => throw p
        case _ => throw e
      }
    }
  }

  /**
   * Internal copy method with defaults.
   */
  private def copyBuilder(
                           environment: Environment = environment,
                           configuration: Configuration = configuration,
                           modules: Seq[SpringableModule] = modules,
                           overrides: Seq[SpringableModule] = overrides,
                           disabled: Seq[Class[_]] = disabled,
                           eagerly: Boolean = eagerly): Self =
    newBuilder(environment, configuration, modules, overrides, disabled, eagerly)

  /**
   * Create a new Self for this immutable builder.
   * Provided by builder implementations.
   */
  protected def newBuilder(
                            environment: Environment,
                            configuration: Configuration,
                            modules: Seq[SpringableModule],
                            overrides: Seq[SpringableModule],
                            disabled: Seq[Class[_]],
                            eagerly: Boolean): Self

}

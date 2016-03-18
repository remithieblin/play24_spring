package play.api.inject.spring

import java.io.File

import play.api.inject._
import play.api.{Configuration, Environment, Mode, PlayException}

import scala.reflect.ClassTag


/**
 * A builder for creating Guice-backed Play Injectors.
 */
abstract class SpringBuilder[Self] protected (
    environment: Environment,
    configuration: Configuration,
    modules: Seq[_],
    overrides: Seq[_],
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
   * Disable modules by class.
   */
  final def disable(moduleClasses: Class[_]*): Self =
    copyBuilder(disabled = disabled ++ moduleClasses)

  /**
   * Override bindings using Spring modules, Play modules, or Play bindings.
   */
  final def overrides(overrideModules: Seq[Binding[_]]): Self =
    copyBuilder(overrides = overrides ++ overrideModules)


  /**
   * Disable module by class.
   */
  final def disable[T](implicit tag: ClassTag[T]): Self = disable(tag.runtimeClass)

  /**
   * Internal copy method with defaults.
   */
  private def copyBuilder(
    environment: Environment = environment,
    configuration: Configuration = configuration,
    modules: Seq[_] = modules,
    overrides: Seq[_] = overrides,
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
    modules: Seq[_],
    overrides: Seq[_],
    disabled: Seq[Class[_]],
    eagerly: Boolean): Self

}













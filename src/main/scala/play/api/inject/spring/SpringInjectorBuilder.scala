//package play.api.inject.spring
//
//import javax.inject.Inject
//
//import org.springframework.context.annotation.AnnotationConfigApplicationContext
//import org.springframework.context.support.GenericApplicationContext
//import play.api.inject.guice.{GuiceLoadException, GuiceKey}
//import play.api.{ Configuration, Environment, Mode, PlayException }
//import java.io.File
//import play.api.inject._
//import play.api.inject.{ Binding => PlayBinding, BindingKey, Injector => PlayInjector, Module => PlayModule }
//
//import scala.reflect.ClassTag
//
//
//
//
///**
// * A builder for creating Guice-backed Play Injectors.
// */
//abstract class GuiceBuilder[Self] protected (
//                                              environment: Environment,
//                                              configuration: Configuration,
//                                              modules: Seq[SpringableModule],
//                                              overrides: Seq[SpringableModule],
//                                              disabled: Seq[Class[_]],
//                                              eagerly: Boolean) {
//
//  /**
//   * Set the environment.
//   */
//  final def in(env: Environment): Self =
//    copyBuilder(environment = env)
//
//  /**
//   * Set the environment path.
//   */
//  final def in(path: File): Self =
//    copyBuilder(environment = environment.copy(rootPath = path))
//
//  /**
//   * Set the environment mode.
//   */
//  final def in(mode: Mode.Mode): Self =
//    copyBuilder(environment = environment.copy(mode = mode))
//
//  /**
//   * Set the environment class loader.
//   */
//  final def in(classLoader: ClassLoader): Self =
//    copyBuilder(environment = environment.copy(classLoader = classLoader))
//
//  /**
//   * Set the dependency initialization to eager.
//   */
//  final def eagerlyLoaded(): Self =
//    copyBuilder(eagerly = true)
//
//  /**
//   * Add additional configuration.
//   */
//  final def configure(conf: Configuration): Self =
//    copyBuilder(configuration = configuration ++ conf)
//
//  /**
//   * Add additional configuration.
//   */
//  final def configure(conf: Map[String, Any]): Self =
//    configure(Configuration.from(conf))
//
//  /**
//   * Add additional configuration.
//   */
//  final def configure(conf: (String, Any)*): Self =
//    configure(conf.toMap)
//
//  /**
//   * Add Guice modules, Play modules, or Play bindings.
//   *
//   * @see [[SpringableModuleConversions]] for the automatically available implicit
//   *      conversions to [[SpringableModule]] from modules and bindings.
//   */
//  final def bindings(bindModules: SpringableModule*): Self =
//    copyBuilder(modules = modules ++ bindModules)
//
//  /**
//   * Override bindings using Guice modules, Play modules, or Play bindings.
//   *
//   * @see [[SpringableModuleConversions]] for the automatically available implicit
//   *      conversions to [[SpringableModule]] from modules and bindings.
//   */
//  final def overrides(overrideModules: SpringableModule*): Self =
//    copyBuilder(overrides = overrides ++ overrideModules)
//
//  /**
//   * Disable modules by class.
//   */
//  final def disable(moduleClasses: Class[_]*): Self =
//    copyBuilder(disabled = disabled ++ moduleClasses)
//
//  /**
//   * Disable module by class.
//   */
//  final def disable[T](implicit tag: ClassTag[T]): Self = disable(tag.runtimeClass)
//
//  /**
//   * Create a Play Injector backed by Guice using this configured builder.
//   */
//  def applicationModule(ctx: GenericApplicationContext): Class[_] = createModule(ctx)
//
//  /**
//   * Creation of the Guice Module used by the injector.
//   * Libraries like Guiceberry and Jukito that want to handle injector creation may find this helpful.
//   */
//  def createModule(ctx: GenericApplicationContext): Class[_] = {
//    import scala.collection.JavaConverters._
//    val injectorModule = SpringableModule.spring(ctx, Seq(
//      bind[PlayInjector].to[SpringInjector],
//      // Java API injector is bound here so that it's available in both
//      // the default application loader and the Java Guice builders
//      bind[play.inject.Injector].to[play.inject.DelegateInjector]
//    ))
//    val enabledModules = modules.map(_.disable(disabled))
//    val bindingModules = SpringableModule.springed(environment, configuration)(enabledModules) :+ injectorModule
//    val overrideModules = SpringableModule.springed(environment, configuration)(overrides)
//
//    ctx = new AnnotationConfigApplicationContext(enabledModules, bindingModules, overrideModules)
//    ctx
////    SpringModules.`override`(bindingModules.asJava).`with`(overrideModules.asJava)
//  }
//
//  /**
//   * Create a Play Injector backed by Guice using this configured builder.
//   */
//  def injector(): PlayInjector = {
//
//    val springContext = applicationContext()
//
//
//    springContext.getBean(classOf[PlayInjector])
//
//  }
//
//  def applicationContext(): GenericApplicationContext = {
//    new GenericApplicationContext()
//  }
//
//  /**
//   * Internal copy method with defaults.
//   */
//  private def copyBuilder(
//                           environment: Environment = environment,
//                           configuration: Configuration = configuration,
//                           modules: Seq[SpringableModule] = modules,
//                           overrides: Seq[SpringableModule] = overrides,
//                           disabled: Seq[Class[_]] = disabled,
//                           eagerly: Boolean = eagerly): Self =
//    newBuilder(environment, configuration, modules, overrides, disabled, eagerly)
//
//  /**
//   * Create a new Self for this immutable builder.
//   * Provided by builder implementations.
//   */
//  protected def newBuilder(
//                            environment: Environment,
//                            configuration: Configuration,
//                            modules: Seq[SpringableModule],
//                            overrides: Seq[SpringableModule],
//                            disabled: Seq[Class[_]],
//                            eagerly: Boolean): Self
//
//}
//
//
//
//
//class SpringInjectorBuilder {
//
//}
//
///**
// * Magnet pattern for creating Guice modules from Play modules or bindings.
// */
//trait SpringableModule {
//  def springed(env: Environment, conf: Configuration): Seq[Class[_]]
//  def disable(classes: Seq[Class[_]]): SpringableModule
//}
//
///**
// * Loading and converting Guice modules.
// */
//object SpringableModule extends SpringableModuleConversions {
//
//  def loadModules(environment: Environment, configuration: Configuration, ctx: GenericApplicationContext): Seq[SpringableModule] = {
//    Modules.locate(environment, configuration) map { module => springable(module, ctx) }
//  }
//
//  /**
//   * Attempt to convert a module of unknown type to a GuiceableModule.
//   */
//  def springable(module: Any, ctx:GenericApplicationContext): SpringableModule = module match {
//    case playModule: Module => fromPlayModule(playModule, ctx)
//    case annotatedClass: Class[_] => fromSpringClass(annotatedClass)
//    case unknown => throw new PlayException(
//      "Unknown module type",
//      s"Module [$unknown] is not a Play module or a Guice module"
//    )
//  }
//
//  /**
//   * Apply GuiceableModules to create Guice modules.
//   */
//  def springed(env: Environment, conf: Configuration)(builders: Seq[SpringableModule]): Seq[Class[_]] =
//    builders flatMap { module => module.springed(env, conf) }
//
//}
//
///**
// * Implicit conversions to SpringableModules.
// */
//trait SpringableModuleConversions {
//
//  import scala.language.implicitConversions
//
//  implicit def fromSpringClass(annotatedClass: Class[_]): SpringableModule = fromSpringClasses(Seq(annotatedClass))
//
//  implicit def fromSpringClasses(annotatedClasses: Seq[Class[_]]): SpringableModule = new SpringableModule {
//    def springed(env: Environment, conf: Configuration): Seq[Class[_]] = annotatedClasses
//    def disable(classes: Seq[Class[_]]): SpringableModule = fromSpringClasses(filterOut(classes, annotatedClasses))
//    override def toString = s"SpringableModule(${annotatedClasses.mkString(", ")})"
//  }
//
//  implicit def fromPlayModule(playModule: Module, ctx: GenericApplicationContext): SpringableModule = fromPlayModules(Seq(playModule), ctx)
//
//  implicit def fromPlayModules(playModules: Seq[Module], ctx: GenericApplicationContext): SpringableModule = new SpringableModule {
//    def springed(env: Environment, conf: Configuration): Seq[Class[_]] = playModules.map(spring(env, conf, ctx))
//    def disable(classes: Seq[Class[_]]): SpringableModule = fromPlayModules(filterOut(classes, playModules), ctx)
//    override def toString = s"SpringableModule(${playModules.mkString(", ")})"
//  }
//
//  implicit def fromPlayBinding(binding: Binding[_], ctx: GenericApplicationContext): SpringableModule = fromPlayBindings(Seq(binding), ctx)
//
//  implicit def fromPlayBindings(bindings: Seq[Binding[_]], ctx: GenericApplicationContext): SpringableModule = new SpringableModule {
//    def springed(env: Environment, conf: Configuration): Seq[Class[_]] = Seq(spring(ctx, bindings))
//    def disable(classes: Seq[Class[_]]): SpringableModule = this // no filtering
//    override def toString = s"SpringableModule(${bindings.mkString(", ")})"
//  }
//
//  private def filterOut[A](classes: Seq[Class[_]], instances: Seq[A]): Seq[A] =
//    instances.filterNot(o => classes.exists(_.isAssignableFrom(o.getClass)))
//
//  /**
//   * Convert the given Play module to a Guice module.
//   */
//  def spring(env: Environment, conf: Configuration, ctx: GenericApplicationContext)(module: Module): Class[_] =
//    spring(ctx, module.bindings(env, conf))
//
//  /**
//   * Convert the given Play bindings to a Spring configuration.
//   */
//  def spring(ctx: GenericApplicationContext, bindings: Seq[Binding[_]]): Class[_] = {
//
//
//    for (b <- bindings) {
////      b.
//    }
//
//
//    new com.google.inject.AbstractModule {
//      def configure(): Unit = {
//        for (b <- bindings) {
//          val binding = b.asInstanceOf[Binding[Any]]
//          val builder = binder().withSource(binding).bind(GuiceKey(binding.key))
//          binding.target.foreach {
////            case ProviderTarget(provider) => builder.toProvider(GuiceProviders.guicify(provider))
//            case ProviderConstructionTarget(provider) => builder.toProvider(provider)
//            case ConstructionTarget(implementation) => builder.to(implementation)
//            case BindingKeyTarget(key) => builder.to(GuiceKey(key))
//          }
//          (binding.scope, binding.eager) match {
//            case (Some(scope), false) => builder.in(scope)
//            case (None, true) => builder.asEagerSingleton()
//            case (Some(scope), true) => throw new GuiceLoadException("A binding must either declare a scope or be eager: " + binding)
//            case _ => // do nothing
//          }
//        }
//      }
//    }
//  }
//
//}
//
///**
// * Play Injector backed by a Guice Injector.
// */
//class SpringInjector @Inject() (springContext: GenericApplicationContext) extends Injector {
//  /**
//   * Get an instance of the given class from the injector.
//   */
//  def instanceOf[T](implicit ct: ClassTag[T]) = instanceOf(ct.runtimeClass.asInstanceOf[Class[T]])
//
//  /**
//   * Get an instance of the given class from the injector.
//   */
//  def instanceOf[T](clazz: Class[T]) = springContext.getBean[T](clazz)
//
//  /**
//   * Get an instance bound to the given binding key.
//   */
//  def instanceOf[T](key: BindingKey[T]) = ???
//}
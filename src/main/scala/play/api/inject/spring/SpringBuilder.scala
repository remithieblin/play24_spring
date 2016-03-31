package play.api.inject.spring

import java.io.File
import java.lang.annotation.Annotation

import config.AppConfig
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver
import org.springframework.beans.factory.config.{ConstructorArgumentValues, AutowireCapableBeanFactory, BeanDefinition}
import org.springframework.beans.factory.support.{AutowireCandidateQualifier, GenericBeanDefinition, DefaultListableBeanFactory}
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.annotation.AnnotationUtils
import play.api.inject._
import play.api.{Configuration, Environment, Mode, PlayException}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag


/**
 * A builder for creating Guice-backed Play Injectors.
 */
abstract class SpringBuilder[Self] protected (
    environment: Environment,
    configuration: Configuration,
    modules: Seq[Module],
    overrides: Seq[Module],
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
   */
  final def bindings(bindModules: Seq[Module]): Self =
    copyBuilder(modules = modules ++ bindModules)

  /**
   * Disable modules by class.
   */
  final def disable(moduleClasses: Class[_]*): Self =
    copyBuilder(disabled = disabled ++ moduleClasses)

  /**
   * Override bindings using Spring modules, Play modules, or Play bindings.
   */
  final def overrides(overrideModules: Seq[Module]): Self =
    copyBuilder(overrides = overrides ++ overrideModules)


  def applicationModule(): Seq[_] = createModule()

  /**
   *
   */
  def createModule(): Seq[Module] = {

    val injectorModule = new Module {
            def bindings(environment: Environment, configuration: Configuration) = Seq(

      // Java API injector is bound here so that it's available in both
      // the default application loader and the Java Guice builders
//      bind[play.inject.Injector].to[play.inject.DelegateInjector]
            //TODO use the package method bind() when moving into actual playframework project

        BindingKey(implicitly[ClassTag[play.inject.Injector]].runtimeClass.asInstanceOf[Class[play.inject.Injector]])
          .to[play.inject.DelegateInjector]
    )}
    val enabledModules: Seq[Module] = filterOut(disabled, modules)
    val bindingModules: Seq[Module] = enabledModules :+ injectorModule
    val springableOverrides: Seq[Module] = overrides.map(SpringableModule.springable)
    bindingModules ++ springableOverrides
  }

  private def filterOut[A](classes: Seq[Class[_]], instances: Seq[A]): Seq[A] =
    instances.filterNot(o => classes.exists(_.isAssignableFrom(o.getClass)))


  /**
   * Disable module by class.
   */
  final def disable[T](implicit tag: ClassTag[T]): Self = disable(tag.runtimeClass)

  def prepareConfig(): Self

  def injector(): Injector = {

    // call parent method that returns a Self
    // overriden in child to load config
    // call parent createModule that creates new modules and return a self
    // finally call the new SpringInjector() method that creates factory and binds everything
//    bindings(createModule()). prepareConfig()

    springInjector()


  }

  private def springInjector(): Injector = {
    val ctx = new AnnotationConfigApplicationContext()

    val beanFactory = ctx.getDefaultListableBeanFactory

    beanFactory.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver())

    val injector = new SpringInjector(beanFactory)
    // Register the Spring injector as a singleton first
    beanFactory.registerSingleton("play-injector", injector)

    //build modules
    val modulesToRegister = applicationModule()

    //register modules
    modulesToRegister.foreach {
      case playModule: Module => playModule.bindings(environment, configuration).foreach(b => bind(beanFactory, b))
      case unknown => throw new PlayException(
        "Unknown module type",
        s"Module [$unknown] is not a Play module or a Guice module"
      )
    }

    ctx.register(classOf[AppConfig])
    //      ctx.scan("provider", "router", "play", "controllers")
    ctx.refresh()

    ctx.start()

    injector
  }

  /**
   * Perhaps this method should be moved into a custom bean definition reader - eg a PlayModuleBeanDefinitionReader.
   */
  private def bind(beanFactory: DefaultListableBeanFactory, binding: Binding[_]) = {

    // Firstly, if it's an unqualified key being bound to an unqualified alias, then there is no need to
    // register anything, Spring by type lookups match all types of the registered bean, there is no need
    // to register aliases for other types.
    val isSimpleTypeAlias = binding.key.qualifier.isEmpty &&
      binding.target.collect {
        case b @ BindingKeyTarget(key) if key.qualifier.isEmpty => b
      }.nonEmpty

    if (!isSimpleTypeAlias) {

      val beanDef = new GenericBeanDefinition()

      // todo - come up with a better name
      val beanName = binding.key.toString()

      // Add qualifier if it exists
      binding.key.qualifier match {
        case Some(QualifierClass(clazz)) =>
          beanDef.addQualifier(new AutowireCandidateQualifier(clazz))
        case Some(QualifierInstance(instance)) =>
          beanDef.addQualifier(qualifierFromInstance(instance))
        case None =>
          // No qualifier, so that means this is the primary binding for that type.
          // Setting primary means that if there are both qualified beans and this bean for the same type,
          // when an unqualified lookup is done, this one will be selected.
          beanDef.setPrimary(true)
      }

      // Start with a scope of prototype, if it's singleton, we'll explicitly set that later
      beanDef.setScope(BeanDefinition.SCOPE_PROTOTYPE)
      // Choose autowire constructor
      beanDef.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR)

      binding.target match {
        case None =>
        // Bound to itself, set the key class as the bean class

        // not registered in GuiceableModuleConversions: def guice(bindings: Seq[PlayBinding[_]]): GuiceModule = {..}:
        //binding.target.foreach {..} => target = None will be ignored, which is the case when binding to self

        //          beanDef.setBeanClass(binding.key.clazz)
        //          SpringApplicationLoader_draft.maybeSetScope(beanDef, binding.key.clazz)

        case Some(ConstructionTarget(clazz)) =>
          // Bound to an implementation, set the impl class as the bean class.
          // In this case, the key class is ignored, since Spring does not key beans by type, but a bean is eligible
          // for autowiring for all supertypes/interafaces.
          beanDef.setBeanClass(clazz.asInstanceOf[Class[_]])
          SpringApplicationLoader.maybeSetScope(beanDef, clazz.asInstanceOf[Class[_]])

        case Some(ProviderConstructionTarget(providerClass)) =>

          val providerBeanName = providerClass.toString
          //          val providerBeanName = beanName + "-provider"
          if (!beanFactory.containsBeanDefinition(providerBeanName)) {

            // The provider itself becomes a bean that gets autowired
            val providerBeanDef = new GenericBeanDefinition()
            providerBeanDef.setBeanClass(providerClass)
            providerBeanDef.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR)
            providerBeanDef.setScope(BeanDefinition.SCOPE_SINGLETON)
            providerBeanDef.setAutowireCandidate(false)
            beanFactory.registerBeanDefinition(providerBeanName, providerBeanDef)
          }

          // And then the provider bean gets used as the factory bean, calling its get method, for the actual bean
          beanDef.setFactoryBeanName(providerBeanName)
          beanDef.setFactoryMethodName("get")
          beanDef.setPrimary(false)
        //          beanDef.setBeanClass(binding.key.clazz)

        case Some(ProviderTarget(provider)) =>

          // We have an actual instance of that provider, we create a factory bean to wrap and invoke that provider instance
          beanDef.setBeanClass(classOf[ProviderFactoryBean[_]])
          val args = new ConstructorArgumentValues()
          args.addIndexedArgumentValue(0, provider)
          args.addIndexedArgumentValue(1, binding.key.clazz)
          args.addIndexedArgumentValue(2, beanFactory)
          beanDef.setConstructorArgumentValues(args)

        case Some(BindingKeyTarget(key)) =>

          // It's an alias, create a factory bean that will look up the alias
          beanDef.setBeanClass(classOf[BindingKeyFactoryBean[_]])
          val args = new ConstructorArgumentValues()
          args.addIndexedArgumentValue(0, key)
          args.addIndexedArgumentValue(1, binding.key.clazz)
          args.addIndexedArgumentValue(2, beanFactory)
          beanDef.setConstructorArgumentValues(args)
      }

      binding.scope match {
        case None =>
        // Do nothing, we've already defaulted or detected the scope
        case Some(scope) =>
          SpringApplicationLoader.setScope(beanDef, scope)
      }

      beanFactory.registerBeanDefinition(beanName, beanDef)
    }
  }

  /**
   * Turns an instance of an annotation into a spring qualifier descriptor.
   */
  private def qualifierFromInstance(instance: Annotation) = {
    val annotationType = instance.annotationType()
    val qualifier = new AutowireCandidateQualifier(annotationType)
    AnnotationUtils.getAnnotationAttributes(instance).asScala.foreach {
      case (attribute, value) => qualifier.setAttribute(attribute, value)
    }

    qualifier
  }

  /**
   * Internal copy method with defaults.
   */
  private def copyBuilder(
    environment: Environment = environment,
    configuration: Configuration = configuration,
    modules: Seq[Module] = modules,
    overrides: Seq[Module] = overrides,
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
    modules: Seq[Module],
    overrides: Seq[Module],
    disabled: Seq[Class[_]],
    eagerly: Boolean): Self

}













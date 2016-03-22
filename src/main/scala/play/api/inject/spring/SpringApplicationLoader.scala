package play.api.inject.spring

import java.lang.annotation.Annotation
import javax.inject.Provider

import config.AppConfig
import org.springframework.beans.factory.annotation.{AutowiredAnnotationBeanPostProcessor, QualifierAnnotationAutowireCandidateResolver}
import org.springframework.beans.factory.config.{AutowireCapableBeanFactory, BeanDefinition, BeanDefinitionHolder, ConstructorArgumentValues}
import org.springframework.beans.factory.support.{DefaultListableBeanFactory, GenericBeanDefinition, _}
import org.springframework.beans.factory.{BeanCreationException, FactoryBean, NoSuchBeanDefinitionException, NoUniqueBeanDefinitionException}
import org.springframework.beans.{BeanInstantiationException, MutablePropertyValues, TypeConverter}
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.annotation.AnnotationUtils
import play.api.ApplicationLoader.Context
import play.api._
import play.api.inject._
import play.core.WebCommands

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

/**
 * based on the awesome work of jroper:
 * https://github.com/jroper/play-spring
 */
class SpringApplicationLoader(protected val initialBuilder: SpringApplicationBuilder) extends ApplicationLoader {

  // empty constructor needed for instantiating via reflection
  def this() = this(new SpringApplicationBuilder)

  def load(context: Context) = {

    builder(context).build()

//    val env = context.environment
//
//
//    //      val configuration = context.initialConfiguration
//    // Create the global first
//    val global = GlobalSettings(context.initialConfiguration, env)
////    BindingKey(classOf[GlobalSettings]) to global,
//
//    // Create the final configuration
//    // todo - abstract this logic out into something pluggable, with the default delegating to global
//    val configuration = global.onLoadConfig(context.initialConfiguration, env.rootPath, env.classLoader, env.mode)
//
//    //      Logger.configure(env.rootPath, configuration, env.mode)
//
//    // Load modules and include some of the core bindings
//    val modules = new Module {
//      def bindings(environment: Environment, configuration: Configuration) = Seq(
//        BindingKey(classOf[GlobalSettings]) to global,
//        BindingKey(classOf[OptionalSourceMapper]) to new OptionalSourceMapper(context.sourceMapper),
//        BindingKey(classOf[WebCommands]) to context.webCommands
//      )} +: Modules.locate(env, configuration)
//
//    val ctx = createApplicationContext(env, configuration, modules)
//    ctx.getBean(classOf[Application])
  }



  /**
   * Construct a builder to use for loading the given context.
   */
  protected def builder(context: ApplicationLoader.Context): SpringApplicationBuilder = {
    initialBuilder
      .in(context.environment)
      .loadConfig(context.initialConfiguration)
      .overrides(overrides(context): Seq[_])
  }

  /**
   * Override some bindings using information from the context. The default
   * implementation of this method provides bindings that most applications
   * should include.
   */
  protected def overrides(context: ApplicationLoader.Context): Seq[_] = {
    SpringApplicationLoader.defaultOverrides(context)
  }

  //    override def createInjector(environment: Environment, configuration: Configuration, modules: Seq[Any]): Option[Injector] = {
  //      Some(createApplicationContext(environment, configuration, modules).getBean(classOf[Injector]))
  //    }



  /**
   * Creates an application context for the given modules
   */
  private def createApplicationContext(environment: Environment, configuration: Configuration, modules: Seq[Any]): ApplicationContext = {

    // todo, use an xml or classpath scanning context or something not dumb
    //      val ctx = new GenericApplicationContext()
    val ctx = new AnnotationConfigApplicationContext()

    //, classOf[Assets], classOf[DefaultHttpErrorHandler]

    val beanFactory = ctx.getDefaultListableBeanFactory

    beanFactory.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver())


    // Register the Spring injector as a singleton first
    beanFactory.registerSingleton("play-injector", new SpringInjector(beanFactory))

    modules.foreach {
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
    ctx
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

}

private object SpringApplicationLoader {

  /**
   * Set the scope on the given bean definition if a scope annotation is declared on the class.
   */
  def maybeSetScope(bd: GenericBeanDefinition, clazz: Class[_]) {
    clazz.getAnnotations.foreach { annotation =>
      if (annotation.annotationType().getAnnotations.exists(_.annotationType() == classOf[javax.inject.Scope])) {
        setScope(bd, annotation.annotationType())
      }
    }
  }

  /**
   * Set the given scope annotation scope on the given bean definition.
   */
  def setScope(bd: GenericBeanDefinition, clazz: Class[_ <: Annotation]) = {
    clazz match {
      case singleton if singleton == classOf[javax.inject.Singleton] =>
        bd.setScope(BeanDefinition.SCOPE_SINGLETON)
      case other =>
      // todo: use Jsr330ScopeMetaDataResolver to resolve and set scope
    }
  }

  /**
   * The default overrides provided by the Scala and Java GuiceApplicationLoaders.
   */
  def defaultOverrides(context: ApplicationLoader.Context) = {
//    val global = GlobalSettings(context.initialConfiguration, context.environment)

    val seq: Seq[_] = Seq(
//      BindingKey(classOf[GlobalSettings]) to global,
      bind[OptionalSourceMapper] to new OptionalSourceMapper(context.sourceMapper),
      bind[WebCommands] to context.webCommands)
    seq
  }

}

/**
 * A factory bean that wraps a provider.
 */
class ProviderFactoryBean[T](provider: Provider[T], objectType: Class[_], factory: AutowireCapableBeanFactory)
  extends FactoryBean[T] {

  lazy val injectedProvider = {
    // Autowire the providers properties - Play needs this in a few places.
    val bpp = new AutowiredAnnotationBeanPostProcessor()
    bpp.setBeanFactory(factory)
    bpp.processInjection(provider)
    provider
  }

  def getObject = injectedProvider.get()

  def getObjectType = objectType

  def isSingleton = false
}

/**
 * A factory bean that wraps a binding key alias.
 */
class BindingKeyFactoryBean[T](key: BindingKey[T], objectType: Class[_], factory: DefaultListableBeanFactory) extends FactoryBean[T] {
  /**
   * The bean name, if it can be determined.
   *
   * Will either return a new bean name, or if the by type lookup should be done on request (in the case of an
   * unqualified lookup because it's cheaper to delegate that to Spring) then do it on request.  Will throw an
   * exception if a key for which no matching bean can be found is found.
   */
  lazy val beanName: Option[String] = {
    key.qualifier match {
      case None =>
        None
      case Some(QualifierClass(qualifier)) =>
        val candidates = factory.getBeanNamesForType(key.clazz)
        val matches = candidates.toList
          .map(name => new BeanDefinitionHolder(factory.getBeanDefinition(name), name))
          .filter { bdh =>
          bdh.getBeanDefinition match {
            case abd: AbstractBeanDefinition =>
              abd.hasQualifier(qualifier.getName)
            case _ => false
          }
        }.map(_.getBeanName)
        getNameFromMatches(matches)
      case Some(QualifierInstance(qualifier)) =>
        val candidates = factory.getBeanNamesForType(key.clazz)
        val matches = candidates.toList
          .map(name => new BeanDefinitionHolder(factory.getBeanDefinition(name), name))
          .filter( bdh => QualifierChecker.checkQualifier(bdh, qualifier, factory.getTypeConverter))
          .map(_.getBeanName)
        getNameFromMatches(matches)
    }
  }

  private def getNameFromMatches(candidates: Seq[String]): Option[String] = {
    candidates match {
      case Nil => throw new NoSuchBeanDefinitionException(key.clazz, "Binding alias for type " + objectType + " to " + key,
        "No bean found for binding alias")
      case single :: Nil => Some(single)
      case multiple => throw new NoUniqueBeanDefinitionException(key.clazz, multiple.asJava)
    }

  }

  def getObject = {
    beanName.fold(factory.getBean(key.clazz))(name => factory.getBean(name).asInstanceOf[T])
  }

  def getObjectType = objectType

  def isSingleton = false
}

/**
 * Hack to expose the checkQualifier method as public.
 */
object QualifierChecker extends QualifierAnnotationAutowireCandidateResolver {

  /**
   * Override to expose as public
   */
  override def checkQualifier(bdHolder: BeanDefinitionHolder, annotation: Annotation, typeConverter: TypeConverter) = {
    bdHolder.getBeanDefinition match {
      case root: RootBeanDefinition => super.checkQualifier(bdHolder, annotation, typeConverter)
      case nonRoot =>
        val bdh = new BeanDefinitionHolder(RootBeanDefinitionCreator.create(nonRoot), bdHolder.getBeanName)
        super.checkQualifier(bdh, annotation, typeConverter)
    }
  }
}

object RootBeanDefinitionCreator {

  def create(bd: BeanDefinition): RootBeanDefinition = {
    val rbd = new RootBeanDefinition()
    rbd.setParentName(bd.getParentName)
    rbd.setBeanClassName(bd.getBeanClassName)
    rbd.setFactoryBeanName(bd.getFactoryBeanName)
    rbd.setFactoryMethodName(bd.getFactoryMethodName)
    rbd.setScope(bd.getScope)
    rbd.setAbstract(bd.isAbstract)
    rbd.setLazyInit(bd.isLazyInit)
    rbd.setRole(bd.getRole)
    rbd.setConstructorArgumentValues(new ConstructorArgumentValues(bd.getConstructorArgumentValues))
    rbd.setPropertyValues(new MutablePropertyValues(bd.getPropertyValues))
    rbd.setSource(bd.getSource)

    val attributeNames = bd.attributeNames
    for ( attributeName <- attributeNames) {
      rbd.setAttribute(attributeName, bd.getAttribute(attributeName))
    }

    bd match {
      case originalAbd: AbstractBeanDefinition =>
        if (originalAbd.hasBeanClass) {
          rbd.setBeanClass(originalAbd.getBeanClass)
        }
        rbd.setAutowireMode(originalAbd.getAutowireMode)
        rbd.setDependencyCheck(originalAbd.getDependencyCheck)
        rbd.setDependsOn(originalAbd.getDependsOn: _*)
        rbd.setAutowireCandidate(originalAbd.isAutowireCandidate)
        rbd.copyQualifiersFrom(originalAbd)
        rbd.setPrimary(originalAbd.isPrimary)
        rbd.setNonPublicAccessAllowed(originalAbd.isNonPublicAccessAllowed)
        rbd.setLenientConstructorResolution(originalAbd.isLenientConstructorResolution)
        rbd.setInitMethodName(originalAbd.getInitMethodName)
        rbd.setEnforceInitMethod(originalAbd.isEnforceInitMethod)
        rbd.setDestroyMethodName(originalAbd.getDestroyMethodName)
        rbd.setEnforceDestroyMethod(originalAbd.isEnforceDestroyMethod)
        rbd.setMethodOverrides(new MethodOverrides(originalAbd.getMethodOverrides))
        rbd.setSynthetic(originalAbd.isSynthetic)
        rbd.setResource(originalAbd.getResource)

      case _ => rbd.setResourceDescription(bd.getResourceDescription)
    }

    rbd
  }
}

class SpringInjector(factory: DefaultListableBeanFactory) extends Injector {

  private val bpp = new AutowiredAnnotationBeanPostProcessor()
  bpp.setBeanFactory(factory)

  def instanceOf[T](implicit ct: ClassTag[T]) = instanceOf(ct.runtimeClass).asInstanceOf[T]

  def instanceOf[T](clazz: Class[T]) = {
    getBean(clazz)
  }

  def getBean[T](clazz: Class[T]): T = {
    try {
      factory.getBean(clazz)
    } catch {

      case e: NoSuchBeanDefinitionException =>
        // if the class is a concrete type, attempt to create a just in time binding
        if (!clazz.isInterface /* todo check if abstract, how? */) {
          println("lol not interface, let's try: " + clazz)
          tryCreate(clazz)
        } else {
          throw e
        }

      case e: BeanInstantiationException =>
        println("lol BeanInstantiationException")
        throw e

      case e: BeanCreationException =>
        println("lol BeanCreationException")
        throw e

      case e: Exception => throw e
    }
  }

  override def instanceOf[T](key: BindingKey[T]): T = {
    getBean(key.clazz)
  }

  def tryCreate[T](clazz: Class[T]) = {
    val beanDef = new GenericBeanDefinition()
    beanDef.setScope(BeanDefinition.SCOPE_PROTOTYPE)
    SpringApplicationLoader.maybeSetScope(beanDef, clazz)
    beanDef.setBeanClass(clazz)
    beanDef.setPrimary(true)
    beanDef.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_AUTODETECT)
    factory.registerBeanDefinition(clazz.toString, beanDef)
    factory.clearMetadataCache()

    val bean = instanceOf(clazz)

    // todo - this ensures fields get injected, see if there's a way that this can be done automatically
    bpp.processInjection(bean)
    bean
  }
}
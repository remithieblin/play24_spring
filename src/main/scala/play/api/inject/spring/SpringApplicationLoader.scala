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
  }



  /**
   * Construct a builder to use for loading the given context.
   */
  protected def builder(context: ApplicationLoader.Context): SpringApplicationBuilder = {
    initialBuilder
      .in(context.environment)
      .loadConfig(context.initialConfiguration)
      .overrides(overrides(context): Seq[Module])
  }

  /**
   * Override some bindings using information from the context. The default
   * implementation of this method provides bindings that most applications
   * should include.
   */
  protected def overrides(context: ApplicationLoader.Context): Seq[Module] = {
    SpringApplicationLoader.defaultOverrides(context)
  }

  //    override def createInjector(environment: Environment, configuration: Configuration, modules: Seq[Any]): Option[Injector] = {
  //      Some(createApplicationContext(environment, configuration, modules).getBean(classOf[Injector]))
  //    }



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
    Seq(
      new Module {
        def bindings(environment: Environment, configuration: Configuration) = Seq(
          bind[OptionalSourceMapper] to new OptionalSourceMapper(context.sourceMapper),
          bind[WebCommands] to context.webCommands)
      }
    )
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
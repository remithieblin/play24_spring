package play.api.inject.spring

import java.lang.annotation.Annotation

import org.springframework.beans.TypeConverter
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver
import org.springframework.beans.factory.config.{BeanDefinition, BeanDefinitionHolder}
import org.springframework.beans.factory.support.{GenericBeanDefinition, _}
import org.springframework.core.annotation.AnnotationUtils
import play.api.ApplicationLoader.Context
import play.api._
import play.api.inject._
import play.core.WebCommands

import scala.collection.JavaConverters._

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
package loader

import javax.inject.{Inject, Singleton}

import config.AppConfig
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import play.api.inject.ApplicationLifecycle
import play.libs.F


@Singleton
class Global @Inject() (lifecycle: ApplicationLifecycle){

  private var context: AnnotationConfigApplicationContext = _

  context = new AnnotationConfigApplicationContext(classOf[AppConfig])
  context.start()
//
//  lifecycle.addStopHook( _ => {
//    context.close()
//    F.Promise.pure( null )
//  })

}

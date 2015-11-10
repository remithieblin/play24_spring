import org.springframework.context.ApplicationContext
import org.springframework.context.support.ClassPathXmlApplicationContext
import play.{Application, GlobalSettings}

class Global extends GlobalSettings {


  private var ctx: ApplicationContext = _

  override def onStart(app: Application) {
//    ctx = new ClassPathXmlApplicationContext("spring-context-data.xml")
  }

  override def onStop(app: Application ) {
    super.onStop(app)
  }


}
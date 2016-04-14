package config

import org.springframework.context.annotation.{ComponentScan, Configuration}

@Configuration
@ComponentScan(Array("com.demo.spring", "controllers"))
class AppConfig  {


}

package config

import org.springframework.context.annotation.{ComponentScan, Configuration}

@Configuration
@ComponentScan(Array("provider", "service", "controllers"))
class AppConfig  {


}

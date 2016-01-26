package config

import org.springframework.context.annotation.{ComponentScan, Configuration}

@Configuration
@ComponentScan(Array("provider", "router"))
class AppConfig {

  println("lol AppConfig")

}

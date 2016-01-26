//package play.api.inject.spring
//
//import play.api.inject._
//import play.api._
//import play.api.ApplicationLoader.Context
//import play.api.inject.spring.bind
//import play.core.{DefaultWebCommands, WebCommands}
//
//class SpringApplicationLoader extends ApplicationLoader {
//
//    def load(context: Context): Application = {
//
//      builder(context).build
////      val env = context.environment
////
////      // Create the global first
////      val global = GlobalSettings(context.initialConfiguration, env)
////
////      // Create the final configuration
////      // todo - abstract this logic out into something pluggable, with the default delegating to global
////      val configuration = global.onLoadConfig(context.initialConfiguration, env.rootPath, env.classLoader, env.mode)
////
////      //      Logger.configure(env.rootPath, configuration, env.mode)
////
////      // Load modules and include some of the core bindings
////      val modules = new Module {
////        def bindings(environment: Environment, configuration: Configuration) = Seq(
////          BindingKey(classOf[GlobalSettings]) to global,
////          BindingKey(classOf[OptionalSourceMapper]) to new OptionalSourceMapper(context.sourceMapper),
////          BindingKey(classOf[WebCommands]) to context.webCommands,
////          bind[WebCommands] to new DefaultWebCommands
////
////        )} +: Modules.locate(env, configuration)
////
//////      val ctx = createApplicationContext(env, configuration, modules)
//////      ctx.getBean(classOf[Application])
//    }
//
//  /**
//   * Construct a builder to use for loading the given context.
//   */
//  protected def builder(context: ApplicationLoader.Context): SpringApplicationBuilder = {
//    initialBuilder
//      .in(context.environment)
//      .loadConfig(context.initialConfiguration)
//      .overrides(overrides(context): _*)
//  }
//
//}
//
//object SpringApplicationLoader {
//  /**
//   * The default overrides provided by the Scala and Java GuiceApplicationLoaders.
//   */
//  def defaultOverrides(context: ApplicationLoader.Context): Seq[SpringableModule] = {
//    Seq(
//      _root_.spring.bind[OptionalSourceMapper] to new OptionalSourceMapper(context.sourceMapper),
//      _root_.spring.bind[WebCommands] to context.webCommands)
//  }
//}

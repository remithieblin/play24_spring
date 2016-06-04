package play.api.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import play.api.libs.Crypto;
import play.libs.concurrent.HttpExecutionContext;

import javax.inject.Singleton;

@Configuration
@ComponentScan(basePackages = {"router", "play", "controllers"},
        includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Singleton.class),
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = Crypto.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = HttpExecutionContext.class)
        }
)
public class CoreConfig {
}

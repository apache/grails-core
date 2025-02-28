package grails.boot

import grails.boot.config.GrailsAutoConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory
import org.springframework.context.annotation.Bean
import spock.lang.Specification

/**
 * Created by graemerocher on 28/05/14.
 */
class GrailsSpringApplicationSpec extends Specification{

    AnnotationConfigServletWebServerApplicationContext context

    void cleanup() {
        context.close()
    }

    void "Test run Grails via SpringApplication"() {
        when:"SpringApplication is used to run a Grails app"
        SpringApplication springApplication  = new SpringApplication(Application)
        springApplication.allowBeanDefinitionOverriding = true
        context = (AnnotationConfigServletWebServerApplicationContext) springApplication.run()

        then:"The application runs"
            context != null
            new URL("http://localhost:${context.webServer.port}/foo/bar").text == 'hello world'
    }


    @EnableAutoConfiguration
    static class Application extends GrailsAutoConfiguration {
        @Bean
        ConfigurableServletWebServerFactory webServerFactory() {
            TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0)
        }
    }
}

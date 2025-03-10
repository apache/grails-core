package demo

@GrabResolver(name='grails-repo', root='https://repo.grails.org/grails/core/')
@Grab("org.grails:grails-gsp-spring-boot:7.0.0-SNAPSHOT")
// if you need to clear snapshots, they are saved to ~/.groovy/grapes

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.SpringApplication
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Bean
import grails.gsp.boot.GspAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration

import grails.gsp.TagLib
import org.springframework.web.servlet.ModelAndView
import java.text.SimpleDateFormat

@RestController
class GspController {
    @RequestMapping("/")
    ModelAndView home() {
        new ModelAndView('index', 'name', 'world')
    }
}

@Component
@TagLib
class FormatTagLib {
	def dateFormat = { attrs, body ->
		out << new SimpleDateFormat(attrs.format).format(attrs.date)
	}
}

@SpringBootApplication
@ImportAutoConfiguration(GspAutoConfiguration.class)
class Application {
    @Bean GspController rspController() { new GspController() }
    @Bean FormatTagLib formatTagLib() { new FormatTagLib() }
}

System.setProperty("org.springframework.boot.logging.LoggingSystem", "none")

SpringApplication.run(Application)
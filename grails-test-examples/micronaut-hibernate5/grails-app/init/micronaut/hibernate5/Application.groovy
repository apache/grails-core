package micronaut.hibernate5

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import groovy.transform.CompileStatic
import io.micronaut.spring.boot.starter.EnableMicronaut

@CompileStatic
@EnableMicronaut
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}

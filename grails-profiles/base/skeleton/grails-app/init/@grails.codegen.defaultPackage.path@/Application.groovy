package @grails.codegen.defaultPackage@

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import org.springframework.context.annotation.ComponentScan

import groovy.transform.CompileStatic

@CompileStatic
@ComponentScan(value = '@grails.codegen.defaultPackage@')
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}

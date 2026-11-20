package @grails.codegen.defaultPackage@

import grails.boot.*
import grails.boot.config.GrailsAutoConfiguration
import grails.plugins.metadata.*
import org.springframework.context.annotation.ComponentScan

@PluginSource
@ComponentScan(value = '@grails.codegen.defaultPackage@')
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}

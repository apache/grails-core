package grails.plugin.formfields

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import grails.plugins.metadata.PluginSource
import groovy.transform.CompileStatic

@CompileStatic
@PluginSource
class Application extends GrailsAutoConfiguration  {

    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}
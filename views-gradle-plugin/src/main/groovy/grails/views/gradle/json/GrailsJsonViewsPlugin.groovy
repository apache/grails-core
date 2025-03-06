package grails.views.gradle.json

import grails.views.gradle.AbstractGroovyTemplatePlugin
import groovy.transform.CompileStatic

/**
 * Concrete implementation of plugin for JSON views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class GrailsJsonViewsPlugin extends AbstractGroovyTemplatePlugin {

    GrailsJsonViewsPlugin() {
        super(JsonViewCompilerTask, 'gson')
    }
}


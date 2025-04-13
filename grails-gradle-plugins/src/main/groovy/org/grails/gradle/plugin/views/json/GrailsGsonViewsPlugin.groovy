package org.grails.gradle.plugin.views.json

import groovy.transform.CompileStatic
import org.grails.gradle.plugin.views.AbstractGroovyTemplatePlugin

/**
 * Concrete implementation of plugin for JSON views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class GrailsGsonViewsPlugin extends AbstractGroovyTemplatePlugin {

    GrailsGsonViewsPlugin() {
        super(GsonViewCompilerTask, 'gson')
    }
}


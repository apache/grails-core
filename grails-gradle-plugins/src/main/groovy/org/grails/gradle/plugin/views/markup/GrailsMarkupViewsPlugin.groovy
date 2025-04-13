package org.grails.gradle.plugin.views.markup

import groovy.transform.CompileStatic
import org.grails.gradle.plugin.views.AbstractGroovyTemplatePlugin

/**
 * A plugin for compiling markup templates
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class GrailsMarkupViewsPlugin extends AbstractGroovyTemplatePlugin {

    GrailsMarkupViewsPlugin() {
        super(MarkupViewCompilerTask, 'gml')
    }
}

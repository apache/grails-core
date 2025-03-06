package grails.views.gradle.markup

import grails.views.gradle.AbstractGroovyTemplatePlugin
import groovy.transform.CompileStatic

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

package grails.views.api

import groovy.transform.CompileStatic

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
trait View {

    /**
     * The locale of the view
     */
    Locale locale = Locale.ENGLISH

    /**
     * The output stream
     */
    Writer out
}
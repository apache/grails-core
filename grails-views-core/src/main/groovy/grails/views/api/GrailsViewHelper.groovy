package grails.views.api

import grails.web.mapping.LinkGenerator
import groovy.transform.CompileStatic

/**
 * Methods available via the 'g' namespace in views
 *
 * @author Graeme Rocher
 */
@CompileStatic
interface GrailsViewHelper extends LinkGenerator {


    /**
     * Obtains a i18n message
     * @param arguments The arguments
     * @return The message
     */
    String message(Map arguments)
}
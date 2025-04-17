package grails.views

import groovy.transform.CompileStatic

/**
 * Interface for views that provider a writer
 *
 * @author Graeme Rocher
 */
@CompileStatic
interface WriterProvider {

    /**
     * @return The current writer
     */
    Writer getOut()
}
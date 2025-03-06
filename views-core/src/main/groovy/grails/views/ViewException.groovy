package grails.views

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

/**
 * Thrown when an exception occurs rendering a view
 *
 * @author Graeme Rocher
 */
@CompileStatic
@InheritConstructors
class ViewException extends RuntimeException{
}

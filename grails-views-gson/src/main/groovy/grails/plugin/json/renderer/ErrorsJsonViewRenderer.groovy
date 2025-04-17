package grails.plugin.json.renderer

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.springframework.validation.Errors

/**
 * Renderer for the errors view
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@InheritConstructors
@CompileStatic
class ErrorsJsonViewRenderer extends AbstractJsonViewContainerRenderer<Errors, Object>{
    final Class<Object> componentType = Object
}

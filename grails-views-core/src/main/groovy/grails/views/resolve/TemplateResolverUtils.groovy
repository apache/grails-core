package grails.views.resolve

import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic

/**
 * Utility methods for resolving template names
 *
 * @since 1.1
 * @author Graeme Rocher
 */
@CompileStatic
class TemplateResolverUtils {

    static String shortTemplateNameForClass(Class<?> cls) {
        def propertyName = GrailsNameUtils.getPropertyName(cls)
        return "/$propertyName/_$propertyName"
    }

    static String fullTemplateNameForClass(Class<?> cls) {
        def templateName = cls.name.replace('.', '/')
        def lastSlash = templateName.lastIndexOf('/')
        def stem = templateName.substring(0, lastSlash)
        return "/$stem/_${GrailsNameUtils.getPropertyName(cls.simpleName)}"
    }
}

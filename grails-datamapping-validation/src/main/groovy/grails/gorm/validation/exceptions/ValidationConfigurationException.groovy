package grails.gorm.validation.exceptions

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

/**
 * An exception thrown when there is an error configuration validation
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class ValidationConfigurationException extends RuntimeException {
    ValidationConfigurationException(String var1) {
        super(var1)
    }

    ValidationConfigurationException(String var1, Throwable var2) {
        super(var1, var2)
    }
}

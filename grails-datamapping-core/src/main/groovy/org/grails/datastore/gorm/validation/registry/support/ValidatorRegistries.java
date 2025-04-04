package org.grails.datastore.gorm.validation.registry.support;

import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry;
import org.grails.datastore.gorm.validation.jakarta.JakartaValidatorRegistry;
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;

/**
 * Utility methods for creating Validator registries
 *
 * @author Graeme Rocher
 * @since 6.1
 */
public class ValidatorRegistries {

    private ValidatorRegistries() {
    }

    /**
     * Creates a validator registry with a static message source
     *
     * @param mappingContext The mapping context
     * @param settings The settings
     * @return The registry
     */
    public static ValidatorRegistry createValidatorRegistry(MappingContext mappingContext, ConnectionSourceSettings settings) {
        return createValidatorRegistry(mappingContext, settings, new StaticMessageSource());
    }

    /**
     * Creates the most appropriate validator registry
     *
     * @param mappingContext The mapping context
     * @param settings the settings
     * @param messageSource the message source
     * @return The registry
     */
    public static ValidatorRegistry createValidatorRegistry(MappingContext mappingContext, ConnectionSourceSettings settings, MessageSource messageSource ) {
        ValidatorRegistry validatorRegistry;
        if(isJakartaValidationAvailable()) {
            validatorRegistry = new JakartaValidatorRegistry(mappingContext, settings, messageSource);
        }
        else {
            validatorRegistry = new DefaultValidatorRegistry(mappingContext, settings, messageSource);
        }
        return validatorRegistry;
    }

    /**
     * @return Whether jakarta.validation is available
     */
    static boolean isJakartaValidationAvailable() {
        return ClassUtils.isPresent("jakarta.validation.Validation");
    }
}

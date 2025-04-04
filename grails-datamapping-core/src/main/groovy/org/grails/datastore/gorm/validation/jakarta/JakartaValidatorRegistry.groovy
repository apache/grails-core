package org.grails.datastore.gorm.validation.jakarta

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.ClassUtils
import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator
import org.springframework.context.ApplicationContext
import org.springframework.context.MessageSource
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.Validator
import org.springframework.validation.annotation.Validated
import org.springframework.validation.beanvalidation.MessageSourceResourceBundleLocator
import org.springframework.validation.beanvalidation.SpringConstraintValidatorFactory

import jakarta.validation.ClockProvider
import jakarta.validation.Configuration
import jakarta.validation.ConstraintValidatorFactory
import jakarta.validation.MessageInterpolator
import jakarta.validation.ParameterNameProvider
import jakarta.validation.TraversableResolver
import jakarta.validation.Validation
import jakarta.validation.ValidatorContext
import jakarta.validation.ValidatorFactory

/**
 * A validator registry that creates validators
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class JakartaValidatorRegistry extends DefaultValidatorRegistry implements ValidatorFactory {

    /**
     * The validator factory
     */
    final ValidatorFactory validatorFactory

    JakartaValidatorRegistry(MappingContext mappingContext, ConnectionSourceSettings settings, MessageSource messageSource = new StaticMessageSource()) {
        super(mappingContext, settings, messageSource)

        Configuration validatorConfiguration = buildConfiguration()
        validatorFactory = buildValidatorFactoryAdapter(validatorConfiguration)
    }

    /**
     * Builds the default Validator configuration
     *
     * @return The configuration
     */
    protected Configuration<?> buildConfiguration() {
        MappingContext context = this.mappingContext
        MessageSource ms = messageSource
        return buildConfigurationFor(context, ms)
    }

    /**
     * Builds a configuration for the given context and message source
     * @param context The context
     * @param messageSource The message source
     * @return The configuration
     */
    static Configuration buildConfigurationFor(MappingContext context, MessageSource messageSource) {
        Configuration<? extends Configuration> validatorConfiguration = Validation.byDefaultProvider()
                .configure()
        validatorConfiguration = validatorConfiguration.ignoreXmlConfiguration()
        validatorConfiguration = validatorConfiguration.traversableResolver(new MappingContextTraversableResolver(context))
        validatorConfiguration = validatorConfiguration.messageInterpolator(new ResourceBundleMessageInterpolator(new MessageSourceResourceBundleLocator(messageSource)))

        if (messageSource instanceof ApplicationContext) {
            validatorConfiguration = validatorConfiguration.constraintValidatorFactory(
                    new SpringConstraintValidatorFactory(((ApplicationContext) messageSource).autowireCapableBeanFactory)
            )
        }

        return validatorConfiguration
    }

    /**
     * Build the validator factory from the validator configuration
     *
     * @param validatorConfiguration The configuration
     * @return The adapter
     */
    protected GormValidatorFactoryAdapter buildValidatorFactoryAdapter(Configuration validatorConfiguration) {
        new GormValidatorFactoryAdapter(validatorConfiguration.buildValidatorFactory())
    }

    @Override
    Validator getValidator(PersistentEntity entity) {
        def ann = entity.javaClass.getAnnotation(Validated)
        if(ann != null && isAvailable()) {
            def validator = validatorFactory.getValidator()
            if(validator instanceof GormValidatorAdapter) {
                return (Validator)validator
            }
            else {
                return new GormValidatorAdapter(validator)
            }
        }
        else {
            return super.getValidator(entity)
        }
    }

    @Override
    jakarta.validation.Validator getValidator() {
        return validatorFactory.getValidator()
    }

    @Override
    ValidatorContext usingContext() {
        return validatorFactory.usingContext()
    }

    @Override
    MessageInterpolator getMessageInterpolator() {
        return validatorFactory.getMessageInterpolator()
    }

    @Override
    TraversableResolver getTraversableResolver() {
        return validatorFactory.getTraversableResolver()
    }

    @Override
    ConstraintValidatorFactory getConstraintValidatorFactory() {
        return validatorFactory.getConstraintValidatorFactory()
    }

    @Override
    ParameterNameProvider getParameterNameProvider() {
        return validatorFactory.getParameterNameProvider()
    }

    @Override
    ClockProvider getClockProvider() {
        return validatorFactory.getClockProvider()
    }

    @Override
    def <T> T unwrap(Class<T> aClass) {
        return validatorFactory.unwrap(aClass)
    }

    @Override
    void close() {
        validatorFactory.close()
    }

    /**
     * @return Whether jakarta.validation is available
     */
    static boolean isAvailable() {
        ClassUtils.isPresent("jakarta.validation.Validation")
    }
}

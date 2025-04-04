package org.grails.datastore.gorm.validation.jakarta

import groovy.transform.CompileStatic

import jakarta.validation.ClockProvider
import jakarta.validation.ConstraintValidatorFactory
import jakarta.validation.MessageInterpolator
import jakarta.validation.ParameterNameProvider
import jakarta.validation.TraversableResolver
import jakarta.validation.Validator
import jakarta.validation.ValidatorContext
import jakarta.validation.ValidatorFactory
import jakarta.validation.valueextraction.ValueExtractor

/**
 * A ValidatorFactory that creates adapted validators
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class GormValidatorFactoryAdapter implements ValidatorFactory  {

    final ValidatorFactory factory

    GormValidatorFactoryAdapter(ValidatorFactory factory) {
        this.factory = factory
    }

    @Override
    ClockProvider getClockProvider() {
        return factory.getClockProvider()
    }

    @Override
    Validator getValidator() {
        return new GormValidatorAdapter(factory.getValidator())
    }

    @Override
    void close() {
        factory.close()
    }

    @Override
    ValidatorContext usingContext() {
        return new GormValidatorContext(factory.usingContext())
    }

    @Override
    MessageInterpolator getMessageInterpolator() {
        factory.getMessageInterpolator()
    }

    @Override
    TraversableResolver getTraversableResolver() {
        return factory.getTraversableResolver()
    }

    @Override
    ConstraintValidatorFactory getConstraintValidatorFactory() {
        return factory.getConstraintValidatorFactory()
    }

    @Override
    ParameterNameProvider getParameterNameProvider() {
        return factory.getParameterNameProvider()
    }

    @Override
    def <T> T unwrap(Class<T> type) {
        return factory.unwrap(type)
    }

    @CompileStatic
    static class GormValidatorContext implements ValidatorContext {
        final ValidatorContext delegate

        GormValidatorContext(ValidatorContext delegate) {
            this.delegate = delegate
        }

        @Override
        ValidatorContext messageInterpolator(MessageInterpolator messageInterpolator) {
            delegate.messageInterpolator(messageInterpolator)
            return this
        }

        @Override
        ValidatorContext traversableResolver(TraversableResolver traversableResolver) {
            delegate.traversableResolver(traversableResolver)
            return this
        }

        @Override
        ValidatorContext constraintValidatorFactory(ConstraintValidatorFactory constraintValidatorFactory) {
            delegate.constraintValidatorFactory(constraintValidatorFactory)
            return this
        }

        @Override
        ValidatorContext parameterNameProvider(ParameterNameProvider parameterNameProvider) {
            delegate.parameterNameProvider(parameterNameProvider)
            return this
        }

        @Override
        ValidatorContext clockProvider(ClockProvider clockProvider) {
            delegate.clockProvider(clockProvider)
            return this
        }

        @Override
        ValidatorContext addValueExtractor(ValueExtractor<?> valueExtractor) {
            delegate.addValueExtractor(valueExtractor)
            return this
        }

        @Override
        Validator getValidator() {
            return new GormValidatorAdapter( delegate.getValidator() )
        }
    }
}

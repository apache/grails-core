/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.datastore.gorm.validation.jakarta

import groovy.transform.CompileStatic

import jakarta.validation.ClockProvider
import jakarta.validation.Configuration
import jakarta.validation.ConstraintValidatorFactory
import jakarta.validation.MessageInterpolator
import jakarta.validation.ParameterNameProvider
import jakarta.validation.TraversableResolver
import jakarta.validation.Validation
import jakarta.validation.ValidatorContext
import jakarta.validation.ValidatorFactory

import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator

import org.springframework.context.ApplicationContext
import org.springframework.context.MessageSource
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.Validator
import org.springframework.validation.annotation.Validated
import org.springframework.validation.beanvalidation.MessageSourceResourceBundleLocator
import org.springframework.validation.beanvalidation.SpringConstraintValidatorFactory

import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.PersistentEntityValidator
import org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.ClassUtils

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
    protected Configuration buildConfiguration() {
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
        Configuration validatorConfiguration = Validation.byDefaultProvider()
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

    /**
     * Builds a validator factory for the given context and message source
     *
     * @param context The context
     * @param messageSource The message source
     * @return The validator factory
     * @since 8.0.1
     */
    static ValidatorFactory createValidatorFactory(MappingContext context, MessageSource messageSource) {
        return buildConfigurationFor(context, messageSource).buildValidatorFactory()
    }

    @Override
    Validator getValidator(PersistentEntity entity) {
        if (!isBeanValidated(entity)) {
            return super.getValidator(entity)
        }
        Validator validator = validatorMap.get(entity)
        if (validator == null) {
            validator = createEntityValidator(entity, messageSource, constraintsEvaluator, constraintRegistry, validatorFactory)
            validatorMap.put(entity, validator)
        }
        return validator
    }

    /**
     * @param entity The entity
     * @return Whether the entity is validated with Bean Validation, i.e. it is annotated with {@link Validated} (GORM
     * adds the annotation to any entity carrying Bean Validation constraint annotations)
     * @since 8.0.1
     */
    static boolean isBeanValidated(PersistentEntity entity) {
        return isAvailable() && entity.javaClass.getAnnotation(Validated) != null
    }

    /**
     * Creates the validator for an entity validated with Bean Validation. It validates the Bean Validation annotations
     * and the GORM constraints of the entity, and exposes both as constraints. The GORM {@code nullable} constraint is
     * left to Bean Validation for the properties whose nullability is declared with an annotation, so a null value
     * is reported once.
     *
     * @param entity The entity
     * @param messageSource The message source
     * @param constraintsEvaluator The evaluator of the GORM constraints
     * @param constraintRegistry The registry the constraints are built with
     * @param validatorFactory The Bean Validation validator factory
     * @return The validator
     * @since 8.0.1
     */
    static GormValidatorAdapter createEntityValidator(PersistentEntity entity, MessageSource messageSource,
            ConstraintsEvaluator constraintsEvaluator, ConstraintRegistry constraintRegistry, ValidatorFactory validatorFactory) {
        jakarta.validation.Validator beanValidator = validatorFactory.getValidator()
        if (beanValidator instanceof GormValidatorAdapter) {
            beanValidator = ((GormValidatorAdapter) beanValidator).thisValidator
        }
        PersistentEntityValidator entityValidator = new PersistentEntityValidator(entity, messageSource, constraintsEvaluator)
        Class<?> entityClass = entity.javaClass
        JakartaConstraintsTranslator.delegateNullabilityToBeanValidation(entityClass, entityValidator.constrainedProperties)
        Map<String, ConstrainedProperty> constrainedProperties = new JakartaConstraintsTranslator()
                .translate(entityClass, entityValidator.constrainedProperties, constraintRegistry)
        return new GormValidatorAdapter(beanValidator, entityValidator, constrainedProperties)
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
    <T> T unwrap(Class<T> aClass) {
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
        ClassUtils.isPresent('jakarta.validation.Validation')
    }
}

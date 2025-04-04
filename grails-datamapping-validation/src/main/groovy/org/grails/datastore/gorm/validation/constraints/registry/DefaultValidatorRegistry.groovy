package org.grails.datastore.gorm.validation.constraints.registry

import grails.gorm.validation.PersistentEntityValidator
import grails.gorm.validation.exceptions.ValidationConfigurationException
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.reflect.ClosureToMapPopulator
import org.grails.datastore.mapping.validation.ValidatorRegistry
import org.springframework.context.MessageSource
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.env.PropertyResolver
import org.springframework.core.env.StandardEnvironment
import org.springframework.validation.Validator
import org.springframework.validation.annotation.Validated

import java.util.concurrent.ConcurrentHashMap

/**
 * A {@link ValidatorRegistry} that builds validators on demand.
 *
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class DefaultValidatorRegistry implements ValidatorRegistry, ConstraintRegistry, ConstraintsEvaluator {

    final Map<PersistentEntity, Validator> validatorMap = new ConcurrentHashMap<>()
    final @Delegate ConstraintsEvaluator constraintsEvaluator
    final @Delegate ConstraintRegistry constraintRegistry
    final MessageSource messageSource
    final MappingContext mappingContext

    DefaultValidatorRegistry(MappingContext mappingContext, ConnectionSourceSettings connectionSourceSettings, MessageSource messageSource = new StaticMessageSource()) {
        this.constraintRegistry = new DefaultConstraintRegistry(messageSource)
        this.messageSource = messageSource
        Map<String, Object> defaultConstraintsMap = resolveDefaultConstraints(connectionSourceSettings)
        this.constraintsEvaluator = new DefaultConstraintEvaluator(constraintRegistry, mappingContext, defaultConstraintsMap)
        this.mappingContext = mappingContext
    }

    protected Map<String, Object> resolveDefaultConstraints( ConnectionSourceSettings connectionSourceSettings ) {
        Closure defaultConstraints = connectionSourceSettings.default.constraints
        Map<String, Object> defaultConstraintsMap = null
        if (defaultConstraints != null) {
            defaultConstraintsMap = [:]
            try {
                new ClosureToMapPopulator(defaultConstraintsMap).populate defaultConstraints
            } catch (Throwable e) {
                throw new ValidationConfigurationException("Error populating default constraints from configuration: ${e.message}", e)
            }
        }
        return defaultConstraintsMap
    }

    @Override
    Validator getValidator(PersistentEntity entity) {
        Validator validator = validatorMap.get(entity)
        if(validator != null) {
            return validator
        }
        else {
            validator = new PersistentEntityValidator(entity, messageSource, constraintsEvaluator)
            validatorMap.put(entity, validator)
        }
        return validator
    }
}

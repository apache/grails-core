package org.grails.datastore.gorm.validation.constraints

import grails.gorm.validation.Constraint
import org.grails.datastore.gorm.validation.constraints.factory.DefaultConstraintFactory
import org.grails.datastore.mapping.model.MappingContext
import org.springframework.context.MessageSource

/**
 * A constraint that restricts constraints to be applicable only to a given {@link org.grails.datastore.mapping.model.MappingContext}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
class MappingContextAwareConstraintFactory extends DefaultConstraintFactory {
    final MappingContext mappingContext

    MappingContextAwareConstraintFactory(Class<? extends Constraint> constraintClass, MessageSource messageSource, MappingContext mappingContext, List<Class> targetTypes = [Object]) {
        super(constraintClass, messageSource, targetTypes)
        this.mappingContext = mappingContext
    }

    @Override
    Constraint build(Class owner, String property, Object constrainingValue) {
        if(mappingContext.getPersistentEntity(owner.name) != null) {
            return super.build(owner, property, constrainingValue)
        }
        return null
    }
}

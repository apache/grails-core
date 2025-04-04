package org.grails.datastore.gorm.validation.constraints.registry

import grails.gorm.validation.Constraint
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.validation.constraints.BlankConstraint
import org.grails.datastore.gorm.validation.constraints.CreditCardConstraint
import org.grails.datastore.gorm.validation.constraints.EmailConstraint
import org.grails.datastore.gorm.validation.constraints.InListConstraint
import org.grails.datastore.gorm.validation.constraints.MatchesConstraint
import org.grails.datastore.gorm.validation.constraints.MaxConstraint
import org.grails.datastore.gorm.validation.constraints.MaxSizeConstraint
import org.grails.datastore.gorm.validation.constraints.MinConstraint
import org.grails.datastore.gorm.validation.constraints.MinSizeConstraint
import org.grails.datastore.gorm.validation.constraints.NotEqualConstraint
import org.grails.datastore.gorm.validation.constraints.NullableConstraint
import org.grails.datastore.gorm.validation.constraints.RangeConstraint
import org.grails.datastore.gorm.validation.constraints.ScaleConstraint
import org.grails.datastore.gorm.validation.constraints.SizeConstraint
import org.grails.datastore.gorm.validation.constraints.UrlConstraint
import org.grails.datastore.gorm.validation.constraints.ValidatorConstraint
import org.grails.datastore.gorm.validation.constraints.factory.ConstraintFactory
import org.grails.datastore.gorm.validation.constraints.factory.DefaultConstraintFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource

import java.util.concurrent.ConcurrentHashMap


/**
 * Default implementation of the {@link ConstraintRegistry} interface. Provides lookup and registration of constraints
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class DefaultConstraintRegistry implements ConstraintRegistry {

    protected Map<String, List<ConstraintFactory>> factoriesByName = new ConcurrentHashMap<String, List<ConstraintFactory>>().withDefault { String name ->
        return [] as List<ConstraintFactory>
    }
    protected Map<Class<? extends Constraint>, List<ConstraintFactory>> factoriesByType = new ConcurrentHashMap<Class<? extends Constraint>, List<ConstraintFactory>>().withDefault { Class<? extends Constraint> type ->
        return [] as List<ConstraintFactory>
    }

    protected final MessageSource messageSource

    DefaultConstraintRegistry(MessageSource messageSource) {
        this.messageSource = messageSource


        def charSequenceType = [CharSequence]
        def comparableNumberType = [Comparable, Number]
        def charSequenceIterableType = [CharSequence, Iterable]

        addConstraint(BlankConstraint, charSequenceType)
        addConstraint(CreditCardConstraint, charSequenceType)
        addConstraint(EmailConstraint, charSequenceType)
        addConstraint(InListConstraint)
        addConstraint(MatchesConstraint, charSequenceType)
        addConstraint(MaxConstraint, comparableNumberType)
        addConstraint(MaxSizeConstraint, charSequenceIterableType)
        addConstraint(MinConstraint, comparableNumberType)
        addConstraint(MinSizeConstraint, charSequenceIterableType)
        addConstraint(NotEqualConstraint)
        addConstraint(NullableConstraint)
        addConstraint(RangeConstraint, comparableNumberType)
        addConstraint(ScaleConstraint, [BigDecimal, Double, Float])
        addConstraint(SizeConstraint, charSequenceIterableType)
        addConstraint(UrlConstraint, charSequenceType)
        addConstraint(ValidatorConstraint)
    }

    @Autowired(required = false)
    void setConstraintFactories(ConstraintFactory... constraintFactories) {
        for(factory in constraintFactories) {
            addConstraintFactory(factory)
        }
    }

    @Override
    void addConstraintFactory(ConstraintFactory factory) {
        factoriesByType.get( factory.type ).add(factory)
        factoriesByName.get( factory.name).add(factory)
    }

    @Override
    void addConstraint(Class<? extends Constraint> constraintClass, List<Class> targetPropertyTypes = [Object] as List<Class>) {
        addConstraintFactory(new DefaultConstraintFactory(constraintClass, messageSource, targetPropertyTypes))
    }

    @Override
    List<ConstraintFactory> findConstraintFactories(String name) {
        return factoriesByName.get(name)
    }

    @Override
    <T extends Constraint> List<ConstraintFactory<T>> findConstraintFactories(Class<T> constraintType) {
        return factoriesByType.get(constraintType) as List<ConstraintFactory<T>>
    }
}

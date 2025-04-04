package org.grails.datastore.gorm.validation.constraints.builtin

import grails.gorm.DetachedCriteria
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.validation.constraints.AbstractConstraint
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.reflect.EntityReflector
import org.springframework.context.MessageSource
import org.springframework.validation.Errors

/**
 * A constraint that validates for the presence of an existing object (uniqueness)
 *
 * @author Graeme Rocher
 * @since 6.0
 *
 */
@CompileStatic
class UniqueConstraint extends AbstractConstraint {

    public static final String NAME = "unique"

    protected List group = []

    UniqueConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource)
        if(constraintParameter instanceof Iterable) {
            for(property in ((Iterable)constraintParameter)) {
                group.add(property.toString())
            }
        }
        else if(constraintParameter instanceof CharSequence) {
            group.add(constraintParameter.toString())
        }
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (constraintParameter instanceof Boolean) {
            return constraintParameter
        } else {
            return constraintParameter instanceof Iterable || constraintParameter instanceof CharSequence
        }
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {

        DetachedCriteria detachedCriteria = new DetachedCriteria(constraintOwningClass)

        MappingContext mappingContext = detachedCriteria.getPersistentEntity()
                                                        .getMappingContext()
        PersistentEntity targetEntity = mappingContext.getPersistentEntity(mappingContext.getProxyHandler().getProxiedClass(target).getName())

        // Determine the GORM class that actually defines this field
        Class<?> constraintClass = constraintOwningClass
        if (!targetEntity.isRoot()) {
            def property = targetEntity.getPropertyByName(constraintPropertyName)
            while (property.isInherited() && targetEntity != null) {
                targetEntity = mappingContext.getPersistentEntity(targetEntity.javaClass.superclass.name)
                if (targetEntity != null) {
                    property = targetEntity.getPropertyByName(constraintPropertyName)
                }
            }
            constraintClass = targetEntity != null? targetEntity.javaClass: constraintClass
        }

        // Re-create the detached criteria based on the new constraint class
        detachedCriteria = new DetachedCriteria(constraintClass)

        if(targetEntity == null) {
            throw new IllegalStateException("Cannot validate object [$target]. It is not a persistent entity")
        }

        EntityReflector reflector = targetEntity.reflector
        String constraintPropertyName = this.constraintPropertyName
        List group = this.group
        
        if(target instanceof DirtyCheckable) {
            Boolean anyChanges = target.hasChanged(constraintPropertyName)
            for(prop in group) {
                anyChanges |= target.hasChanged(prop.toString())
            }
            if(!anyChanges) {
                return
            }
        }

        PersistentProperty persistentProperty = targetEntity.getPropertyByName(constraintPropertyName)
        boolean isToOne = persistentProperty instanceof ToOne
        if(isToOne) {
            PersistentEntity association = ((Association) persistentProperty).getAssociatedEntity()
            if (!association.mappingContext.proxyHandler.isProxy(propertyValue)) {
                def associationId = association.reflector.getIdentifier(propertyValue)
                if(associationId == null) {
                    // unsaved entity
                    return
                }
                // replace with proxy to prevent trying to flush transient instance
                propertyValue = GormEnhancer.findStaticApi(association.javaClass).load(associationId)
            }
        }

        if (constraintParameter) {
            boolean shouldValidate = true
            final ProxyHandler proxyHandler = targetEntity.getMappingContext().proxyHandler
            detachedCriteria = detachedCriteria.build {
                eq(constraintPropertyName, propertyValue)
                if (!group.isEmpty()) {
                    for (prop in group) {
                        String propName = prop.toString()
                        def value = reflector.getProperty(target, propName)
                        if (value != null) {
                            PersistentProperty associated = targetEntity.getPropertyByName(propName)
                            if (associated instanceof ToOne) {
                                // We are merely verifying that the object is not transient here
                                def associationId
                                if (proxyHandler.isProxy(value)) {
                                    associationId = proxyHandler.getIdentifier(value)
                                } else {
                                    associationId = ((Association) associated).getAssociatedEntity().getReflector().getIdentifier(value)
                                }
                                if (associationId == null) {
                                    // no need to validate since this group association is unsaved
                                    shouldValidate = false

                                    // we aren't validating, so no point continuing
                                    continue
                                }
                            }
                            eq propName, value
                        }
                    }
                }
            }.id()

            if (shouldValidate) {
                def existingId = detachedCriteria.get()
                if (existingId != null) {
                    // We are merely verifying that the object is not transient here
                    def targetId
                    if (proxyHandler.isProxy(target)) {
                        if (proxyHandler.isInitialized(target)) {
                            targetId = reflector.getIdentifier(proxyHandler.unwrap(target))
                        } else {
                            targetId = proxyHandler.getIdentifier(target)
                        }
                    } else {
                        targetId = reflector.getIdentifier(target)
                    }
                    if (targetId != existingId) {
                        def args = [constraintPropertyName, constraintOwningClass, propertyValue] as Object[]
                        rejectValue(target, errors, "unique", args, getDefaultMessage("default.not.unique.message"))
                    }
                }
            }
        }
    }


    @Override
    boolean supports(Class type) {
        return true
    }

    @Override
    String getName() {
        return NAME
    }
}

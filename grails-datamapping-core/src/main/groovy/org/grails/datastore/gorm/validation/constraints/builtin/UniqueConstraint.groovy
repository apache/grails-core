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

package org.grails.datastore.gorm.validation.constraints.builtin

import groovy.transform.CompileStatic

import org.springframework.context.MessageSource
import org.springframework.validation.Errors

import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.mapping.config.UniqueNulls
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.gorm.validation.constraints.AbstractConstraint
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.reflect.EntityReflector

/**
 * A constraint that validates for the presence of an existing object (uniqueness).
 *
 * <p>The parameter is {@code true}, the name of one property or a list of property names that group the constraint,
 * or a map {@code [group: ..., nulls: ...]}. A {@code null} in the constrained property or in its group is read as the
 * {@code nulls} option says, else as the {@code grails.gorm.unique.nulls} setting says, else as the unique index of the
 * database does; see {@link UniqueNulls}.</p>
 *
 * @author Graeme Rocher
 * @since 6.0
 *
 */
@CompileStatic
class UniqueConstraint extends AbstractConstraint {

    public static final String NAME = 'unique'

    public static final String GROUP = 'group'

    public static final String NULLS = 'nulls'

    protected List group = []

    /** The reading the constraint asks for, or {@code null} to follow the datastore. */
    protected UniqueNulls nulls

    UniqueConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource)
        Object groupParameter = constraintParameter
        if (constraintParameter instanceof Map) {
            groupParameter = ((Map) constraintParameter).get(GROUP)
            nulls = UniqueNulls.of(((Map) constraintParameter).get(NULLS))
        }
        if (groupParameter instanceof Iterable) {
            for (property in ((Iterable) groupParameter)) {
                group.add(property.toString())
            }
        }
        else if (groupParameter instanceof CharSequence) {
            group.add(groupParameter.toString())
        }
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (constraintParameter instanceof Boolean) {
            return constraintParameter
        }
        if (constraintParameter instanceof Map) {
            Map options = (Map) constraintParameter
            Object groupOption = options.get(GROUP)
            // an empty map is falsy, so the constraint would validate nothing without a word
            boolean knownKeys = !options.isEmpty() && options.keySet().every { it == GROUP || it == NULLS }
            boolean validGroup = groupOption == null || groupOption instanceof Iterable || groupOption instanceof CharSequence
            if (!knownKeys || !validGroup) {
                throw new IllegalArgumentException("The unique constraint of [${constraintOwningClass.name}.${constraintPropertyName}] takes a map with a '${GROUP}' (a property name or a list of them) and a '${NULLS}' option, not ${options}")
            }
            UniqueNulls.of(options.get(NULLS))
            return options
        }
        return constraintParameter instanceof Iterable || constraintParameter instanceof CharSequence
    }

    /**
     * The constrained property is checked when it is {@code null}, since whether a {@code null} is a duplicate
     * depends on the reading of nulls.
     */
    @Override
    protected boolean skipNullValues() {
        return false
    }

    /**
     * Returns the reading of nulls for the entities of the datastore: the constraint's own option, else the datastore's
     * setting, with {@link UniqueNulls#DATABASE} resolved to what the database's unique index does.
     */
    protected UniqueNulls resolveNulls(Datastore datastore) {
        if (datastore == null) {
            return nulls == null || nulls == UniqueNulls.DATABASE ? UniqueNulls.VALUE : nulls
        }
        if (nulls == null) {
            return datastore.getUniqueNulls()
        }
        return nulls == UniqueNulls.DATABASE ? datastore.getDatabaseUniqueNulls() : nulls
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
            constraintClass = targetEntity != null ? targetEntity.javaClass : constraintClass
        }

        // Re-create the detached criteria based on the new constraint class
        detachedCriteria = new DetachedCriteria(constraintClass)

        if (targetEntity == null) {
            throw new IllegalStateException("Cannot validate object [$target]. It is not a persistent entity")
        }

        UniqueNulls nullsReading = resolveNulls(GormRegistry.findDatastore(constraintClass))
        if (propertyValue == null && nullsReading != UniqueNulls.VALUE) {
            // a null constrained value is not a value to be unique, unless nulls are values
            return
        }

        EntityReflector reflector = targetEntity.reflector
        String constraintPropertyName = this.constraintPropertyName
        List group = this.group

        if (target instanceof DirtyCheckable) {
            Boolean anyChanges = target.hasChanged(constraintPropertyName)
            for (prop in group) {
                anyChanges |= target.hasChanged(prop.toString())
            }
            if (!anyChanges) {
                return
            }
        }

        PersistentProperty persistentProperty = targetEntity.getPropertyByName(constraintPropertyName)
        boolean isToOne = persistentProperty instanceof ToOne
        if (isToOne && propertyValue != null) {
            PersistentEntity association = ((Association) persistentProperty).getAssociatedEntity()
            if (!association.mappingContext.proxyHandler.isProxy(propertyValue)) {
                def associationId = association.reflector.getIdentifier(propertyValue)
                if (associationId == null) {
                    // unsaved entity
                    return
                }
                // replace with proxy to prevent trying to flush transient instance
                propertyValue = GormRegistry.instance.findStaticApi(association.javaClass).load(associationId)
            }
        }

        if (constraintParameter) {
            boolean shouldValidate = true
            final ProxyHandler proxyHandler = targetEntity.getMappingContext().proxyHandler
            detachedCriteria = detachedCriteria.build {
                if (propertyValue == null) {
                    isNull(constraintPropertyName)
                } else {
                    eq(constraintPropertyName, propertyValue)
                }
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
                            eq(propName, value)
                        } else if (nullsReading == UniqueNulls.VALUE) {
                            // null is compared as a value: only a row whose value is also null conflicts
                            isNull(propName)
                        } else if (nullsReading == UniqueNulls.SKIP) {
                            // a null in the key is never a duplicate, as in a SQL unique predicate
                            shouldValidate = false
                        }
                        // UniqueNulls.WILDCARD: a null group value leaves its property out, so it matches every row
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
                        rejectValue(target, errors, 'unique', args, getDefaultMessage('default.not.unique.message'))
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

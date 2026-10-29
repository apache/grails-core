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

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

import groovy.transform.CompileStatic

import jakarta.validation.Validator
import jakarta.validation.groups.Default
import jakarta.validation.metadata.CascadableDescriptor
import jakarta.validation.metadata.ContainerElementTypeDescriptor
import jakarta.validation.metadata.GroupConversionDescriptor
import jakarta.validation.metadata.PropertyDescriptor

import org.springframework.context.MessageSource
import org.springframework.validation.Errors

import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.PersistentEntityValidator
import org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association

/**
 * Validates a persistent entity annotated with Jakarta Bean Validation constraints: its GORM constraints (the
 * {@code constraints} block and the defaults) as a {@link PersistentEntityValidator}, then its annotations with the
 * Bean Validation provider, whose violations are reported as field errors with Spring's Bean Validation message
 * codes (such as {@code NotNull.member.name}, {@code NotNull.name} and {@code NotNull}).
 * <p>
 * The GORM {@code nullable} constraint is left to Bean Validation for a property whose nullability an annotation
 * of the validated groups declares ({@code @NotNull}, {@code @NotBlank}, {@code @NotEmpty} or {@code @Null}), so a
 * null value is reported once. Bean Validation does not cascade: the GORM validator cascades to the associations
 * as it does for any entity, and validates an annotated associated entity both ways through
 * {@link #validateBeyondConstraints}. An object is validated in the default group, and an object associated
 * through a property marked {@code @Valid} in the groups of the object owning it, converted as the
 * {@code @ConvertGroup} annotations of the property declare, as Bean Validation cascades. Any other associated
 * object is validated in the default group, as it is on its own.
 * <p>
 * {@link #getConstrainedProperties()} describes the annotations as GORM constraints next to the ones of the block,
 * see {@link JakartaConstraintsTranslator}.
 *
 * @since 8.0.1
 */
@CompileStatic
class JakartaPersistentEntityValidator extends PersistentEntityValidator {

    /**
     * The validator for the Bean Validation annotations of the entity
     */
    final GormValidatorAdapter beanValidator

    private final JakartaConstraintsTranslator translator
    private final ConstraintsEvaluator constraintsEvaluator
    private final Map<String, ConstrainedProperty> describedConstrainedProperties
    private final Map<List<Class<?>>, Map<String, ConstrainedProperty>> validatedConstrainedPropertiesByGroups = new ConcurrentHashMap<>()

    /**
     * @param entity The entity
     * @param messageSource The message source
     * @param constraintsEvaluator The evaluator of the GORM constraints
     * @param constraintRegistry The registry the constraints describing the annotations are built with
     * @param beanValidator The Bean Validation validator
     */
    JakartaPersistentEntityValidator(PersistentEntity entity, MessageSource messageSource, ConstraintsEvaluator constraintsEvaluator,
            ConstraintRegistry constraintRegistry, Validator beanValidator) {
        super(entity, messageSource, constraintsEvaluator)
        this.beanValidator = beanValidator instanceof GormValidatorAdapter
                ? (GormValidatorAdapter) beanValidator
                : new GormValidatorAdapter(beanValidator)
        this.constraintsEvaluator = constraintsEvaluator
        this.translator = new JakartaConstraintsTranslator(this.beanValidator, targetClass)
        Map<String, ConstrainedProperty> validated = super.getValidatedConstrainedProperties(NO_VALIDATION_HINTS)
        translator.delegateNullabilityToBeanValidation(validated)
        this.describedConstrainedProperties = Collections.unmodifiableMap(translator.translate(validated, constraintRegistry))
    }

    /**
     * @return The constraints of the entity, the Bean Validation annotations described as GORM constraints next to
     * the ones of the {@code constraints} block. The annotations themselves are validated by Bean Validation, see
     * {@link #getValidatedConstrainedProperties} for the constraints this validator validates.
     */
    @Override
    Map<String, ConstrainedProperty> getConstrainedProperties() {
        return describedConstrainedProperties
    }

    /**
     * The constraints of the block, with the nullability of the properties an annotation of the groups the object
     * is validated in checks for null left to Bean Validation.
     */
    @Override
    protected Map<String, ConstrainedProperty> getValidatedConstrainedProperties(Object[] hints) {
        List<Class<?>> groups = groupsOf(hints)
        if (groups.isEmpty()) {
            return super.getValidatedConstrainedProperties(hints)
        }
        return validatedConstrainedPropertiesByGroups.computeIfAbsent(groups, { List<Class<?>> key ->
            Map<String, ConstrainedProperty> evaluated = constraintsEvaluator.evaluate(targetClass)
            translator.delegateNullabilityToBeanValidation(evaluated, key.toArray(new Class<?>[0]))
            return Collections.unmodifiableMap(evaluated)
        } as Function<List<Class<?>>, Map<String, ConstrainedProperty>>)
    }

    /**
     * Validates the Bean Validation annotations of the object in the groups it is validated in, without cascading,
     * as the GORM validator cascades.
     */
    @Override
    protected void validateBeyondConstraints(Object obj, Errors errors, Object[] hints) {
        beanValidator.validate(obj, errors, false, groupsOf(hints).toArray(new Class<?>[0]))
    }

    /**
     * The groups an object associated through a property marked {@code @Valid} is validated in: the groups of the
     * object owning it, converted as the {@code @ConvertGroup} annotations of the property declare. An object
     * associated through any other property is validated in the default group, as it is on its own.
     */
    @Override
    protected Object[] getAssociationValidationHints(Association association, Object[] hints) {
        CascadableDescriptor cascaded = findCascadedDescriptor(association.name)
        if (cascaded == null) {
            return NO_VALIDATION_HINTS
        }
        Map<Class<?>, Class<?>> conversions = new HashMap<>()
        for (GroupConversionDescriptor conversion in cascaded.groupConversions) {
            conversions.put(conversion.from, conversion.to)
        }
        List<Class<?>> groups = groupsOf(hints) ?: Collections.<Class<?>> singletonList(Default)
        List<Class<?>> converted = groups.collect { Class<?> group -> conversions.get(group) ?: group }
        return converted == [Default] ? NO_VALIDATION_HINTS : converted.toArray()
    }

    private CascadableDescriptor findCascadedDescriptor(String propertyName) {
        PropertyDescriptor property = translator.beanDescriptor.getConstraintsForProperty(propertyName)
        if (property == null || property.isCascaded()) {
            return property
        }
        return property.constrainedContainerElementTypes.find { ContainerElementTypeDescriptor element -> element.isCascaded() }
    }

    /**
     * @return The Bean Validation groups among the validation hints, none for the default group
     */
    private static List<Class<?>> groupsOf(Object[] hints) {
        List<Class<?>> groups = new ArrayList<>()
        for (Object hint in hints) {
            if (hint instanceof Class) {
                groups.add((Class<?>) hint)
            }
        }
        return groups == [Default] ? Collections.<Class<?>> emptyList() : groups
    }
}

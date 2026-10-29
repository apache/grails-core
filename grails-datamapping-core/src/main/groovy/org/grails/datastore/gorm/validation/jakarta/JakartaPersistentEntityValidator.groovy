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
 * Validates a persistent entity constrained with Jakarta Bean Validation annotations: its GORM constraints (the
 * {@code constraints} block and the defaults) as a {@link PersistentEntityValidator}, then its annotations with the
 * Bean Validation provider, whose violations are reported as field errors with Spring's Bean Validation message
 * codes (such as {@code NotNull.member.name}, {@code NotNull.name} and {@code NotNull}).
 * <p>
 * The GORM {@code nullable} constraint is left to Bean Validation for a property whose nullability an annotation
 * of the validated groups declares ({@code @NotNull}, {@code @NotBlank}, {@code @NotEmpty} or {@code @Null}), so a
 * null value is reported once. The GORM validator cascades to the persistent associations as it does for any
 * entity, and validates an annotated associated entity both ways through {@link #validateBeyondConstraints};
 * Bean Validation cascades only through the other properties marked {@code @Valid}, such as a transient one. An
 * object is validated in the default group, or in the groups given as validation hints to
 * {@link #validate(Object, Errors, Object...)}, and an object associated through a property marked {@code @Valid}
 * in the groups of the object owning it, converted as the {@code @ConvertGroup} annotations of the property
 * declare, as Bean Validation cascades, see {@link #getAssociationValidationHints}. Any other associated object is
 * validated in the default group, as it is on its own. An associated object reached through two associations is
 * validated once in each distinct list of groups. A group sequence is validated group by group, each group over
 * the object and the objects it cascades to, and stops at the first group with a violation among them, see
 * {@link #validateAssociationsAndBeyond}.
 * <p>
 * {@link #getConstrainedProperties()} describes the annotations as GORM constraints next to the ones of the block,
 * see {@link JakartaConstraintsTranslator}.
 *
 * @since 8.0.1
 */
@CompileStatic
class JakartaPersistentEntityValidator extends PersistentEntityValidator {

    private static final List<Class<?>> DEFAULT_GROUP_LIST = Collections.<Class<?>> singletonList(Default)

    /**
     * The validator for the Bean Validation annotations of the entity
     */
    final GormValidatorAdapter beanValidator

    private final JakartaConstraintsTranslator translator
    private final ConstraintsEvaluator constraintsEvaluator
    private final Map<String, ConstrainedProperty> describedConstrainedProperties
    private final Map<List<Object>, Map<String, ConstrainedProperty>> validatedConstrainedPropertiesByGroups = new ConcurrentHashMap<>()

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
        Set<Class<?>> excluded = excludedGroupsOf(hints)
        if (groups.isEmpty() && excluded.isEmpty()) {
            return super.getValidatedConstrainedProperties(hints)
        }
        List<Object> key = [groups, excluded] as List<Object>
        return validatedConstrainedPropertiesByGroups.computeIfAbsent(key, { List<Object> k ->
            Map<String, ConstrainedProperty> evaluated = constraintsEvaluator.evaluate(targetClass)
            translator.delegateNullabilityToBeanValidation(evaluated, groups ?: DEFAULT_GROUP_LIST, excluded)
            return Collections.unmodifiableMap(evaluated)
        } as Function<List<Object>, Map<String, ConstrainedProperty>>)
    }

    /**
     * Validates the object in the groups among its hints as Bean Validation validates an object in them: in the
     * groups that are not group sequences together, cascading to the associations with them, then in each group
     * sequence group by group, cascading with each group, and stopping at the first group with a violation of the
     * object or of an object it cascades to. The constraints of the block of the object are validated before, once,
     * with all the groups, and the ones of an associated object when it is first reached, so a violation of them
     * stops a sequence as a violation of the group it is reached in does.
     */
    @Override
    protected void validateAssociationsAndBeyond(Object obj, Errors errors, boolean cascade, Object[] hints, Set validatedObjects) {
        List<Class<?>> groups = groupsOf(hints) ?: DEFAULT_GROUP_LIST
        Set<Class<?>> excluded = excludedGroupsOf(hints)
        List<Class<?>> singleGroups = groups.findAll { Class<?> group -> !JakartaConstraintsTranslator.isGroupSequence(group) }
        if (singleGroups) {
            validateInGroups(obj, errors, cascade, singleGroups, excluded, validatedObjects)
        }
        for (Class<?> sequence in groups) {
            if (!JakartaConstraintsTranslator.isGroupSequence(sequence)) {
                continue
            }
            for (Class<?> group in JakartaConstraintsTranslator.sequenceGroupsOf(sequence)) {
                int errorCount = errors.errorCount
                validateInGroups(obj, errors, cascade, Collections.<Class<?>> singletonList(group), excluded, validatedObjects)
                if (errors.errorCount > errorCount) {
                    break
                }
            }
        }
    }

    private void validateInGroups(Object obj, Errors errors, boolean cascade, List<Class<?>> groups, Set<Class<?>> excluded, Set validatedObjects) {
        Object[] stepHints = hintsOf(groups, excluded)
        if (cascade) {
            cascadeToAssociations(obj, entity, errors, entityReflector, stepHints, validatedObjects)
        }
        validateBeyondConstraints(obj, errors, stepHints)
    }

    /**
     * Validates the Bean Validation annotations of the object in the groups among its hints, cascading only through
     * the properties marked {@code @Valid} that are not persistent associations, as the GORM validator cascades to
     * those. The violations of the groups the object is not validated in, see {@link ExcludedGroups}, are left out.
     */
    @Override
    protected void validateBeyondConstraints(Object obj, Errors errors, Object[] hints) {
        List<Class<?>> groups = groupsOf(hints)
        Set<Class<?>> excluded = excludedGroupsOf(hints)
        Set<Class<?>> reported = excluded.isEmpty() ? null : reportedGroupsOf(groups ?: DEFAULT_GROUP_LIST, excluded)
        beanValidator.validate(obj, errors, false, groups.toArray(new Class<?>[0]), reported)
    }

    /**
     * The groups whose constraints a validation of the entity in the given groups reports: the groups the provider
     * validates with them, without the excluded ones, and, for the default group, the groups the entity redefines
     * it with.
     */
    private Set<Class<?>> reportedGroupsOf(List<Class<?>> groups, Set<Class<?>> excluded) {
        Set<Class<?>> reported = new LinkedHashSet<>()
        for (Class<?> group in groups) {
            reported.addAll(JakartaConstraintsTranslator.validatedGroupsOf(group))
        }
        reported.removeAll(excluded)
        if (reported.contains(Default)) {
            reported.addAll(translator.defaultGroups)
        }
        return reported
    }

    /**
     * The groups an object associated through a property marked {@code @Valid} is validated in: the groups of the
     * object owning it, converted as the {@code @ConvertGroup} annotations of the property declare. An object
     * associated through any other property is validated in the default group, as it is on its own.
     * <p>
     * As Bean Validation cascades, a conversion applies to each group the provider validates when the owner is
     * validated in the groups of its hints: a group and the groups it extends, and, for a group sequence, its groups
     * in their order, each with the groups it extends. A group a conversion converts is validated as the group it
     * converts to, together with the groups that one extends or sequences. A group no conversion converts is
     * validated alone, without the groups it extends, which the owner validates, and converts, for themselves: when
     * one of them is converted, the provider validates the associated object in it nonetheless, so the hints exclude
     * it, see {@link ExcludedGroups}.
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
        List<Class<?>> converted = new ArrayList<>()
        Set<Class<?>> validated = new HashSet<>()
        Set<Class<?>> expanded = new HashSet<>()
        for (Class<?> group in validationOrderOf(hints)) {
            Class<?> target = conversions.get(group)
            if (target != null) {
                addGroup(converted, target)
                validated.addAll(JakartaConstraintsTranslator.validatedGroupsOf(target))
            }
            else {
                addGroup(converted, group)
                validated.add(group)
                expanded.addAll(JakartaConstraintsTranslator.validatedGroupsOf(group))
            }
        }
        expanded.removeAll(validated)
        return hintsOf(converted, expanded)
    }

    /**
     * The groups the provider validates an object with the given hints in, in its order: the groups among the
     * hints, each with the groups it extends, a group sequence as its groups in their order, without the excluded
     * groups.
     */
    private static List<Class<?>> validationOrderOf(Object[] hints) {
        Set<Class<?>> order = new LinkedHashSet<>()
        for (Class<?> group in (groupsOf(hints) ?: DEFAULT_GROUP_LIST)) {
            order.addAll(JakartaConstraintsTranslator.validatedGroupsOf(group))
        }
        order.removeAll(excludedGroupsOf(hints))
        return new ArrayList<>(order)
    }

    private static void addGroup(List<Class<?>> groups, Class<?> group) {
        if (!groups.contains(group)) {
            groups.add(group)
        }
    }

    private CascadableDescriptor findCascadedDescriptor(String propertyName) {
        PropertyDescriptor property = translator.beanDescriptor.getConstraintsForProperty(propertyName)
        if (property == null || property.isCascaded()) {
            return property
        }
        return property.constrainedContainerElementTypes.find { ContainerElementTypeDescriptor element -> element.isCascaded() }
    }

    /**
     * @return The hints for the groups, the default group alone being no hints
     */
    private static Object[] hintsOf(List<Class<?>> groups, Set<Class<?>> excluded) {
        if (excluded.isEmpty()) {
            return groups == DEFAULT_GROUP_LIST ? NO_VALIDATION_HINTS : groups.toArray()
        }
        List<Object> hints = new ArrayList<Object>(groups)
        hints.add(new ExcludedGroups(excluded))
        return hints.toArray()
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
        return groups == DEFAULT_GROUP_LIST ? Collections.<Class<?>> emptyList() : groups
    }

    /**
     * @return The groups the validation hints exclude, none when they exclude no group
     */
    private static Set<Class<?>> excludedGroupsOf(Object[] hints) {
        for (Object hint in hints) {
            if (hint instanceof ExcludedGroups) {
                return ((ExcludedGroups) hint).groups
            }
        }
        return Collections.<Class<?>> emptySet()
    }

    /**
     * A validation hint excluding groups from a validation: the provider validates a group together with the
     * groups it extends, whereas Bean Validation cascading validates an associated object in a group of its owner
     * no conversion converts alone, and in the groups it extends only as the owner validates them, each converted
     * or not by itself. The groups a group extends that are converted are excluded from the validation of the
     * associated object in the unconverted group: their violations are not reported, and their annotations do not
     * relax the {@code nullable} constraint of the block.
     *
     * @since 8.0.1
     */
    static final class ExcludedGroups {

        /**
         * The excluded groups
         */
        final Set<Class<?>> groups

        ExcludedGroups(Collection<Class<?>> groups) {
            this.groups = Collections.unmodifiableSet(new LinkedHashSet<Class<?>>(groups))
        }

        @Override
        boolean equals(Object other) {
            return other instanceof ExcludedGroups && groups == ((ExcludedGroups) other).groups
        }

        @Override
        int hashCode() {
            return groups.hashCode()
        }

        @Override
        String toString() {
            return "excluding ${groups*.simpleName}"
        }
    }
}

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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import jakarta.validation.ConstraintViolation
import jakarta.validation.ElementKind
import jakarta.validation.Path
import jakarta.validation.Validator
import jakarta.validation.executable.ExecutableValidator
import jakarta.validation.groups.Default
import jakarta.validation.metadata.CascadableDescriptor
import jakarta.validation.metadata.ConstraintDescriptor
import jakarta.validation.metadata.ContainerElementTypeDescriptor
import jakarta.validation.metadata.GroupConversionDescriptor
import jakarta.validation.metadata.PropertyDescriptor

import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.beanvalidation.SpringValidatorAdapter

import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.gorm.validation.CascadingValidator

/**
 * A validator adapter that applies translates the constraint errors into the Errors object of a GORM entity
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class GormValidatorAdapter extends SpringValidatorAdapter implements CascadingValidator {

    /**
     * Whether the validation in progress on the thread cascades to the persistent associations of an entity. When
     * {@code false}, the GORM validator cascades to them, and Bean Validation cascades only through the properties
     * marked {@code @Valid} that are not persistent associations, see {@link MappingContextTraversableResolver}.
     */
    public static final ThreadLocal<Boolean> CASCADE_VALIDATION = new ThreadLocal<>()

    final Validator thisValidator

    GormValidatorAdapter(Validator targetValidator) {
        super(targetValidator)
        thisValidator = targetValidator
    }

    @Override
    void validate(Object obj, Errors errors, boolean cascade) {
        CASCADE_VALIDATION.set(cascade)
        try {
            validate(obj, errors)
        } finally {
            CASCADE_VALIDATION.remove()
        }
    }

    /**
     * Validates the object in the given groups, or in the default group when there are none
     *
     * @param obj The object to validate
     * @param errors The errors to report the violations to
     * @param cascade Whether validation cascades to the persistent associations of the entity through the
     * properties marked {@code @Valid}; it cascades through the other properties marked {@code @Valid} either way
     * @param groups The groups to validate
     * @since 8.0.1
     */
    void validate(Object obj, Errors errors, boolean cascade, Class<?>[] groups) {
        CASCADE_VALIDATION.set(cascade)
        try {
            validate(obj, errors, (Object[]) groups)
        } finally {
            CASCADE_VALIDATION.remove()
        }
    }

    /**
     * Validates the object in the given groups as {@link #validate(Object, Errors, boolean, Class[])} does,
     * reporting only the violations of the constraints declared in one of the reported groups. The provider
     * validates a group together with the groups it extends; when validation cascades from an object validated in
     * a group whose extended groups a {@code @ConvertGroup} converts, the associated object is validated in the
     * group alone, so the violations of the converted groups are left out. The reported groups are the ones of the
     * object; for an object the provider cascades to through the properties marked {@code @Valid}, they are converted
     * along the path of the violation as the {@code @ConvertGroup} annotations of those properties declare, the way
     * the provider converts the groups it validates in, see {@link #isReported}.
     *
     * @param obj The object to validate
     * @param errors The errors to report the violations to
     * @param cascade Whether validation cascades to the persistent associations of the entity
     * @param groups The groups to validate
     * @param reportedGroups The groups whose violations to report, null to report every violation
     * @since 8.0.1
     */
    void validate(Object obj, Errors errors, boolean cascade, Class<?>[] groups, Set<Class<?>> reportedGroups) {
        if (reportedGroups == null) {
            validate(obj, errors, cascade, groups)
            return
        }
        CASCADE_VALIDATION.set(cascade)
        try {
            Set<ConstraintViolation<Object>> reported = new LinkedHashSet<>()
            for (ConstraintViolation<Object> violation : thisValidator.validate(obj, groups)) {
                if (isReported(violation, reportedGroups)) {
                    reported.add(violation)
                }
            }
            processConstraintViolations(reported, errors)
        } finally {
            CASCADE_VALIDATION.remove()
        }
    }

    /**
     * Whether the violation is of a constraint declared in one of the reported groups, as they apply to the bean
     * holding the constraint. The provider cascades to a bean through a property marked {@code @Valid} once for each
     * group it validates the owner in, converting the group as the {@code @ConvertGroup} annotations of the property
     * declare: a converted group is validated as the group it converts to, with the groups that one extends or
     * sequences, and an unconverted group alone. The reported groups follow the same conversions along the path of
     * the violation, from the validated object to the bean holding the constraint, so a violation the provider found
     * through a conversion of a reported group is reported, and one it found through a group that is not reported is
     * not. The default group of a bean stands for the groups of the sequence redefining it, if any.
     */
    private boolean isReported(ConstraintViolation<?> violation, Set<Class<?>> reportedGroups) {
        List<Path.Node> nodes = violation.propertyPath.toList()
        Class<?> beanClass = violation.rootBeanClass
        Set<Class<?>> groups = reportedGroups
        // the last node is the constrained element, the nodes before it the properties the provider cascaded through
        for (int i = 0; i < nodes.size() - 1; i++) {
            Path.Node node = nodes.get(i)
            if (node.kind != ElementKind.PROPERTY) {
                continue
            }
            PropertyDescriptor property = thisValidator.getConstraintsForClass(beanClass).getConstraintsForProperty(node.name)
            CascadableDescriptor cascaded = JakartaConstraintsTranslator.cascadedDescriptorOf(property)
            if (cascaded != null) {
                groups = convertedGroupsOf(withDefaultGroupsOf(groups, beanClass), cascaded.groupConversions)
            }
            beanClass = i == nodes.size() - 2
                    ? (violation.leafBean?.getClass() ?: Object)
                    : cascadedClassOf(property, nodes.get(i + 1))
        }
        return !Collections.disjoint(violation.constraintDescriptor.groups, withDefaultGroupsOf(groups, beanClass))
    }

    /**
     * The groups the provider cascades to a bean with when it validates the owner in the given groups, converted as
     * the given conversions declare: each converted group as the group it converts to, with the groups that one
     * extends or sequences, and each other group alone.
     */
    private static Set<Class<?>> convertedGroupsOf(Set<Class<?>> groups, Set<GroupConversionDescriptor> conversions) {
        if (conversions.isEmpty()) {
            return groups
        }
        Map<Class<?>, Class<?>> targets = new HashMap<>()
        for (GroupConversionDescriptor conversion in conversions) {
            targets.put(conversion.from, conversion.to)
        }
        Set<Class<?>> converted = new LinkedHashSet<>()
        for (Class<?> group in groups) {
            Class<?> target = targets.get(group)
            if (target == null) {
                converted.add(group)
            }
            else {
                converted.addAll(JakartaConstraintsTranslator.validatedGroupsOf(target))
            }
        }
        return converted
    }

    /**
     * The groups with, when the default group is among them, the groups the class redefines it with
     */
    private static Set<Class<?>> withDefaultGroupsOf(Set<Class<?>> groups, Class<?> beanClass) {
        if (!groups.contains(Default)) {
            return groups
        }
        Set<Class<?>> expanded = new LinkedHashSet<>(groups)
        expanded.addAll(JakartaConstraintsTranslator.defaultGroupsOf(beanClass))
        return expanded
    }

    /**
     * The declared class of the bean a cascaded property leads to: the cascaded type argument of the container when
     * the next node of the path is an element of one, otherwise the type of the property, {@code Object} when unknown
     */
    private static Class<?> cascadedClassOf(PropertyDescriptor property, Path.Node next) {
        if (property == null) {
            return Object
        }
        if (next.isInIterable()) {
            ContainerElementTypeDescriptor element = property.constrainedContainerElementTypes
                    .find { ContainerElementTypeDescriptor candidate -> candidate.isCascaded() }
            return element?.elementClass ?: Object
        }
        return property.elementClass ?: Object
    }

    @Override
    <T> Set<ConstraintViolation<T>> validate(T object, Class<?>[] groups) {
        def constraintViolations = super.validate(object, groups)
        if (object instanceof GormValidateable) {
            def errors = ((GormValidateable) object).getErrors()
            processConstraintViolations(constraintViolations, errors)
        }
        return constraintViolations
    }

    /**
     * Reports a violation of a class level constraint as an error of the nested path of the errors, such as
     * {@code lines[0]} when GORM cascades validation to an associated object, as Spring reports it when Bean
     * Validation cascades through {@code @Valid}. Without a nested path it remains a global error.
     */
    @Override
    protected void processConstraintViolations(Set<ConstraintViolation<Object>> violations, Errors errors) {
        String nestedPath = errors.getNestedPath()
        if (!nestedPath) {
            super.processConstraintViolations(violations, errors)
            return
        }
        String path = nestedPath.endsWith(Errors.NESTED_PATH_SEPARATOR)
                ? nestedPath.substring(0, nestedPath.length() - Errors.NESTED_PATH_SEPARATOR.length())
                : nestedPath
        for (ConstraintViolation<Object> violation : violations) {
            if (determineField(violation)) {
                super.processConstraintViolations(Collections.singleton(violation), errors)
                continue
            }
            FieldError fieldError = errors.getFieldError('')
            if (fieldError == null || !fieldError.isBindingFailure()) {
                ConstraintDescriptor<?> descriptor = violation.getConstraintDescriptor()
                errors.rejectValue('', determineErrorCode(descriptor),
                        getArgumentsForConstraint(errors.getObjectName(), path, descriptor), violation.getMessage())
            }
        }
    }

    @Override
    @CompileDynamic
    ExecutableValidator forExecutables() {
        return thisValidator.forExecutables()
    }
}

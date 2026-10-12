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

import java.beans.Introspector
import java.lang.annotation.Annotation
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.math.RoundingMode

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import jakarta.validation.Constraint
import jakarta.validation.constraints.AssertFalse
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Negative
import jakarta.validation.constraints.NegativeOrZero
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

import org.springframework.context.support.StaticMessageSource
import org.springframework.util.ClassUtils

import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.DefaultConstrainedProperty
import grails.gorm.validation.exceptions.ValidationConfigurationException
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher

/**
 * Describes the Jakarta Bean Validation annotations of a class in terms of GORM constraints, so that the constraint
 * metadata of an entity annotated with them ({@link grails.gorm.validation.ConstrainedEntity}) reflects the
 * annotations as well as the {@code constraints} block. The annotations themselves are still validated by Bean
 * Validation, the translated constraints only describe them: a property annotated with {@code @NotNull} reports
 * {@code nullable == false}, one annotated with {@code @Size(max = 10)} reports {@code maxSize == 10}, and so on.
 *
 * Annotations without a GORM equivalent (such as {@code @Past}, {@code @Digits} or custom constraints) are described
 * through the constraints composing them when they are composed constraints, and left out otherwise.
 *
 * @since 8.0.1
 */
@Slf4j
@CompileStatic
class JakartaConstraintsTranslator {

    /**
     * The constraint annotations on the fields and getters of the class and its superclasses, keyed by property name.
     * An annotation is a constraint when its type is annotated with {@link Constraint}, which covers custom
     * constraints. Repeatable containers such as {@code Pattern.List} are expanded.
     *
     * @param clazz The class
     * @return The constraint annotations by property name
     */
    static Map<String, List<Annotation>> findConstraintAnnotations(Class<?> clazz) {
        Map<String, List<Annotation>> result = new LinkedHashMap<>()
        for (Class<?> current = clazz; current != null && current != Object; current = current.superclass) {
            for (Field field in current.declaredFields) {
                if (!field.synthetic && !Modifier.isStatic(field.modifiers)) {
                    collectConstraintAnnotations(result, field.name, field.annotations)
                }
            }
            for (Method method in current.declaredMethods) {
                if (!method.synthetic && !Modifier.isStatic(method.modifiers) && method.parameterCount == 0) {
                    String propertyName = getterPropertyName(method)
                    if (propertyName != null) {
                        collectConstraintAnnotations(result, propertyName, method.annotations)
                    }
                }
            }
        }
        return result
    }

    /**
     * @param annotation The annotation
     * @return Whether the annotation is a Bean Validation constraint
     */
    static boolean isConstraintAnnotation(Annotation annotation) {
        return annotation.annotationType().isAnnotationPresent(Constraint)
    }

    /**
     * The properties whose nullability is declared through an annotation ({@code @NotNull}, {@code @NotBlank},
     * {@code @NotEmpty} or {@code @Null}, directly or through a composed constraint).
     *
     * @param clazz The class
     * @return The property names
     */
    static Set<String> findNullabilityConstrainedProperties(Class<?> clazz) {
        Set<String> result = new LinkedHashSet<>()
        findConstraintAnnotations(clazz).each { String propertyName, List<Annotation> annotations ->
            if (annotations.any { Annotation annotation -> constrainsNullability(annotation) }) {
                result.add(propertyName)
            }
        }
        return result
    }

    private static boolean constrainsNullability(Annotation annotation) {
        if (annotation instanceof NotNull || annotation instanceof NotBlank || annotation instanceof NotEmpty || annotation instanceof Null) {
            return true
        }
        return composingConstraints(annotation).any { Annotation composing -> constrainsNullability(composing) }
    }

    /**
     * Leaves the nullability of the given properties to Bean Validation by relaxing the GORM {@code nullable}
     * constraint, so that a {@code null} value is reported once rather than by both validators. The constraints are
     * modified in place.
     *
     * @param clazz The class the constraints were evaluated for
     * @param constraints The evaluated GORM constraints
     */
    static void delegateNullabilityToBeanValidation(Class<?> clazz, Map<String, ConstrainedProperty> constraints) {
        for (String propertyName in findNullabilityConstrainedProperties(clazz)) {
            ConstrainedProperty constrained = constraints.get(propertyName)
            if (constrained != null && constrained.hasAppliedConstraint(ConstrainedProperty.NULLABLE_CONSTRAINT)) {
                constrained.applyConstraint(ConstrainedProperty.NULLABLE_CONSTRAINT, true)
            }
        }
    }

    /**
     * Describes the Bean Validation annotations of the class as GORM constraints, on top of a copy of the given
     * constraints. The given constraints are not modified, as they are the ones the GORM validator validates with.
     *
     * @param clazz The annotated class
     * @param constraints The GORM constraints of the class, typically evaluated from its {@code constraints} block
     * @param registry The registry to build the constraints with
     * @return A new map with the merged constraints, or the given map when the class has no constraint annotations
     */
    Map<String, ConstrainedProperty> translate(Class<?> clazz, Map<String, ConstrainedProperty> constraints, ConstraintRegistry registry) {
        Map<String, List<Annotation>> annotationsByProperty = findConstraintAnnotations(clazz)
        if (annotationsByProperty.isEmpty()) {
            return constraints
        }
        ConstraintRegistry targetRegistry = registry ?: new DefaultConstraintRegistry(new StaticMessageSource())
        Map<String, ConstrainedProperty> merged = new LinkedHashMap<>(constraints ?: Collections.<String, ConstrainedProperty> emptyMap())
        annotationsByProperty.each { String propertyName, List<Annotation> annotations ->
            ConstrainedProperty existing = constraints?.get(propertyName)
            DefaultConstrainedProperty constrained
            if (existing != null) {
                constrained = copy(existing, targetRegistry)
            }
            else {
                Class<?> propertyType = ClassPropertyFetcher.getPropertyType(clazz, propertyName)
                if (propertyType == null) {
                    return
                }
                constrained = new DefaultConstrainedProperty(clazz, propertyName, propertyType, targetRegistry)
                constrained.order = merged.size() + 1
            }
            for (Annotation annotation in annotations) {
                applyAnnotation(constrained, annotation)
            }
            merged.put(propertyName, constrained)
        }
        return merged
    }

    /**
     * A new constrained property with the same constraints and metadata as the given one.
     *
     * @param source The constrained property to copy
     * @param registry The registry to build the constraints with
     * @return The copy
     */
    static DefaultConstrainedProperty copy(ConstrainedProperty source, ConstraintRegistry registry) {
        DefaultConstrainedProperty target = new DefaultConstrainedProperty(source.owner, source.propertyName, source.propertyType, registry)
        for (grails.gorm.validation.Constraint constraint in source.appliedConstraints) {
            try {
                target.applyConstraint(constraint.name, constraint.parameter)
            }
            catch (Exception e) {
                log.warn('Unable to copy the [{}] constraint of [{}.{}]: {}', constraint.name, source.owner.name, source.propertyName, e.message)
            }
        }
        if (source instanceof DefaultConstrainedProperty) {
            DefaultConstrainedProperty defaults = (DefaultConstrainedProperty) source
            defaults.metaConstraints.each { String name, Object value -> target.addMetaConstraint(name, value) }
            target.order = defaults.order
            target.displayType = defaults.displayType
            target.editable = defaults.editable
            target.format = defaults.format
            target.widget = defaults.widget
            target.password = defaults.password
            target.attributes = defaults.attributes
        }
        return target
    }

    /**
     * Applies the GORM constraints describing the annotation. Bounds tighten the ones already applied rather than
     * replace them.
     *
     * @param constrained The constrained property to describe the annotation on
     * @param annotation The annotation
     */
    void applyAnnotation(DefaultConstrainedProperty constrained, Annotation annotation) {
        switch (annotation) {
            case NotNull:
                applyIfSupported(constrained, ConstrainedProperty.NULLABLE_CONSTRAINT, false)
                break
            case NotBlank:
                applyIfSupported(constrained, ConstrainedProperty.NULLABLE_CONSTRAINT, false)
                applyIfSupported(constrained, ConstrainedProperty.BLANK_CONSTRAINT, false)
                break
            case NotEmpty:
                applyIfSupported(constrained, ConstrainedProperty.NULLABLE_CONSTRAINT, false)
                tightenMinSize(constrained, 1)
                break
            case Null:
                applyIfSupported(constrained, ConstrainedProperty.NULLABLE_CONSTRAINT, true)
                break
            case Size:
                Size size = (Size) annotation
                if (size.min() > 0) {
                    tightenMinSize(constrained, size.min())
                }
                if (size.max() < Integer.MAX_VALUE) {
                    tightenMaxSize(constrained, size.max())
                }
                break
            case Min:
                tightenMin(constrained, ((Min) annotation).value())
                break
            case Max:
                tightenMax(constrained, ((Max) annotation).value())
                break
            case DecimalMin:
                DecimalMin decimalMin = (DecimalMin) annotation
                BigDecimal min = new BigDecimal(decimalMin.value())
                if (decimalMin.inclusive()) {
                    tightenMin(constrained, min)
                }
                else if (isIntegral(constrained.propertyType)) {
                    tightenMin(constrained, min.setScale(0, RoundingMode.FLOOR).add(BigDecimal.ONE))
                }
                break
            case DecimalMax:
                DecimalMax decimalMax = (DecimalMax) annotation
                BigDecimal max = new BigDecimal(decimalMax.value())
                if (decimalMax.inclusive()) {
                    tightenMax(constrained, max)
                }
                else if (isIntegral(constrained.propertyType)) {
                    tightenMax(constrained, max.setScale(0, RoundingMode.CEILING).subtract(BigDecimal.ONE))
                }
                break
            case Positive:
                if (isIntegral(constrained.propertyType)) {
                    tightenMin(constrained, 1)
                }
                break
            case PositiveOrZero:
                tightenMin(constrained, 0)
                break
            case Negative:
                if (isIntegral(constrained.propertyType)) {
                    tightenMax(constrained, -1)
                }
                break
            case NegativeOrZero:
                tightenMax(constrained, 0)
                break
            case Email:
                applyIfSupported(constrained, ConstrainedProperty.EMAIL_CONSTRAINT, true)
                Email email = (Email) annotation
                if (email.regexp() != '.*') {
                    applyMatches(constrained, withInlineFlags(email.regexp(), email.flags()))
                }
                break
            case Pattern:
                Pattern pattern = (Pattern) annotation
                applyMatches(constrained, withInlineFlags(pattern.regexp(), pattern.flags()))
                break
            case AssertTrue:
                applyIfSupported(constrained, ConstrainedProperty.IN_LIST_CONSTRAINT, [Boolean.TRUE])
                break
            case AssertFalse:
                applyIfSupported(constrained, ConstrainedProperty.IN_LIST_CONSTRAINT, [Boolean.FALSE])
                break
            default:
                if (!applyHibernateValidatorAnnotation(constrained, annotation)) {
                    for (Annotation composing in composingConstraints(annotation)) {
                        applyAnnotation(constrained, composing)
                    }
                }
        }
    }

    /**
     * The Hibernate Validator specific annotations with a GORM equivalent, matched by name so that no provider is
     * required.
     */
    private boolean applyHibernateValidatorAnnotation(DefaultConstrainedProperty constrained, Annotation annotation) {
        switch (annotation.annotationType().name) {
            case 'org.hibernate.validator.constraints.Length':
                int min = (int) annotationMember(annotation, 'min')
                int max = (int) annotationMember(annotation, 'max')
                if (min > 0) {
                    tightenMinSize(constrained, min)
                }
                if (max < Integer.MAX_VALUE) {
                    tightenMaxSize(constrained, max)
                }
                return true
            case 'org.hibernate.validator.constraints.Range':
                tightenMin(constrained, (long) annotationMember(annotation, 'min'))
                tightenMax(constrained, (long) annotationMember(annotation, 'max'))
                return true
            case 'org.hibernate.validator.constraints.URL':
                applyIfSupported(constrained, ConstrainedProperty.URL_CONSTRAINT, true)
                return true
            case 'org.hibernate.validator.constraints.CreditCardNumber':
                applyIfSupported(constrained, ConstrainedProperty.CREDIT_CARD_CONSTRAINT, true)
                return true
            default:
                return false
        }
    }

    /** The constraints a composed constraint is made of, i.e. the constraint annotations on its type */
    private static List<Annotation> composingConstraints(Annotation annotation) {
        List<Annotation> composing = []
        for (Annotation meta in annotation.annotationType().annotations) {
            if (isConstraintAnnotation(meta)) {
                composing.add(meta)
            }
            else if (isRepeatableContainer(meta)) {
                composing.addAll(containedAnnotations(meta))
            }
        }
        return composing
    }

    private static void collectConstraintAnnotations(Map<String, List<Annotation>> result, String propertyName, Annotation[] annotations) {
        for (Annotation annotation in annotations) {
            if (isConstraintAnnotation(annotation)) {
                result.computeIfAbsent(propertyName, { String key -> new ArrayList<Annotation>() }).add(annotation)
            }
            else if (isRepeatableContainer(annotation)) {
                collectConstraintAnnotations(result, propertyName, containedAnnotations(annotation))
            }
        }
    }

    /** Repeated constraints are read back as their nested container annotation, e.g. {@code Pattern.List} */
    private static boolean isRepeatableContainer(Annotation annotation) {
        Class<?> enclosing = annotation.annotationType().enclosingClass
        return enclosing != null && enclosing.isAnnotation() && enclosing.isAnnotationPresent(Constraint)
    }

    private static Annotation[] containedAnnotations(Annotation container) {
        Object value = annotationMember(container, 'value')
        return value instanceof Annotation[] ? (Annotation[]) value : new Annotation[0]
    }

    private static Object annotationMember(Annotation annotation, String name) {
        try {
            return annotation.annotationType().getMethod(name).invoke(annotation)
        }
        catch (ReflectiveOperationException ignored) {
            return null
        }
    }

    private static String getterPropertyName(Method method) {
        String name = method.name
        if (name.startsWith('get') && name.length() > 3 && method.returnType != Void.TYPE) {
            return Introspector.decapitalize(name.substring(3))
        }
        if (name.startsWith('is') && name.length() > 2 && (method.returnType == Boolean.TYPE || method.returnType == Boolean)) {
            return Introspector.decapitalize(name.substring(2))
        }
        return null
    }

    /** The embedded flag expression letters of {@link java.util.regex.Pattern}, CANON_EQ has none */
    private static final Map<Pattern.Flag, String> INLINE_FLAGS = [
            (Pattern.Flag.UNIX_LINES): 'd',
            (Pattern.Flag.CASE_INSENSITIVE): 'i',
            (Pattern.Flag.COMMENTS): 'x',
            (Pattern.Flag.MULTILINE): 'm',
            (Pattern.Flag.DOTALL): 's',
            (Pattern.Flag.UNICODE_CASE): 'u'
    ].asImmutable()

    /**
     * The GORM {@code matches} constraint takes a bare regular expression, so the flags of the annotation are
     * expressed as an embedded flag expression, e.g. {@code (?i)} for {@code CASE_INSENSITIVE}.
     *
     * @param regexp The regular expression
     * @param flags The flags
     * @return The regular expression with the flags embedded
     */
    static String withInlineFlags(String regexp, Pattern.Flag[] flags) {
        String letters = flags.collect { Pattern.Flag flag -> INLINE_FLAGS.get(flag) }.findAll().join('')
        return letters ? "(?${letters})${regexp}".toString() : regexp
    }

    private void applyMatches(DefaultConstrainedProperty constrained, String regexp) {
        if (!constrained.hasAppliedConstraint(ConstrainedProperty.MATCHES_CONSTRAINT)) {
            applyIfSupported(constrained, ConstrainedProperty.MATCHES_CONSTRAINT, regexp)
        }
    }

    private void tightenMinSize(DefaultConstrainedProperty constrained, int minSize) {
        Integer current = constrained.minSize
        if (current == null || minSize > current) {
            applyIfSupported(constrained, ConstrainedProperty.MIN_SIZE_CONSTRAINT, minSize)
        }
    }

    private void tightenMaxSize(DefaultConstrainedProperty constrained, int maxSize) {
        Integer current = constrained.maxSize
        if (current == null || maxSize < current) {
            applyIfSupported(constrained, ConstrainedProperty.MAX_SIZE_CONSTRAINT, maxSize)
        }
    }

    private void tightenMin(DefaultConstrainedProperty constrained, Number min) {
        Comparable value = convertNumber(min, constrained.propertyType)
        if (value != null && (constrained.min == null || value.compareTo(constrained.min) > 0)) {
            applyIfSupported(constrained, ConstrainedProperty.MIN_CONSTRAINT, value)
        }
    }

    private void tightenMax(DefaultConstrainedProperty constrained, Number max) {
        Comparable value = convertNumber(max, constrained.propertyType)
        if (value != null && (constrained.max == null || value.compareTo(constrained.max) < 0)) {
            applyIfSupported(constrained, ConstrainedProperty.MAX_CONSTRAINT, value)
        }
    }

    private boolean applyIfSupported(DefaultConstrainedProperty constrained, String constraintName, Object value) {
        if (!constrained.supportsContraint(constraintName)) {
            return false
        }
        try {
            constrained.applyConstraint(constraintName, value)
            return true
        }
        catch (ValidationConfigurationException e) {
            log.warn('Unable to describe a Bean Validation annotation on [{}.{}] with the [{}] constraint: {}',
                    constrained.owner.name, constrained.propertyName, constraintName, e.message)
            return false
        }
    }

    private static boolean isIntegral(Class<?> propertyType) {
        Class<?> type = boxed(propertyType)
        return type == Integer || type == Long || type == Short || type == Byte || type == BigInteger
    }

    /**
     * Converts a bound to the property's own type, which the GORM {@code min} and {@code max} constraints require.
     *
     * @return The converted bound, or null when the property is not numeric or the bound does not fit in its type
     */
    private static Comparable convertNumber(Number value, Class<?> propertyType) {
        Class<?> type = boxed(propertyType)
        BigDecimal decimal = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString())
        try {
            if (type == BigDecimal) {
                return decimal
            }
            if (type == Double) {
                return decimal.doubleValue()
            }
            if (type == Float) {
                return decimal.floatValue()
            }
            if (type == Integer) {
                return decimal.intValueExact()
            }
            if (type == Long) {
                return decimal.longValueExact()
            }
            if (type == Short) {
                return decimal.shortValueExact()
            }
            if (type == Byte) {
                return decimal.byteValueExact()
            }
            if (type == BigInteger) {
                return decimal.toBigIntegerExact()
            }
        }
        catch (ArithmeticException ignored) {
            // the bound does not fit in the property type
        }
        return null
    }

    private static Class<?> boxed(Class<?> type) {
        return ClassUtils.resolvePrimitiveIfNecessary(type)
    }
}

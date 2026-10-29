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

import java.lang.annotation.Annotation
import java.math.RoundingMode

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import jakarta.validation.GroupSequence
import jakarta.validation.Validator
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
import jakarta.validation.groups.Default
import jakarta.validation.metadata.BeanDescriptor
import jakarta.validation.metadata.ConstraintDescriptor
import jakarta.validation.metadata.PropertyDescriptor
import jakarta.validation.metadata.Scope

import org.springframework.context.support.StaticMessageSource
import org.springframework.util.ClassUtils
import org.springframework.util.ReflectionUtils

import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.DefaultConstrainedProperty
import grails.gorm.validation.exceptions.ValidationConfigurationException
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher

/**
 * Describes the Jakarta Bean Validation constraints of a class in terms of GORM constraints, so that the constraint
 * metadata of an entity annotated with them ({@link grails.gorm.validation.ConstrainedEntity}) reflects the
 * annotations as well as the {@code constraints} block. The annotations themselves are still validated by Bean
 * Validation, the translated constraints only describe them: a property annotated with {@code @NotNull} reports
 * {@code nullable == false}, one annotated with {@code @Size(max = 10)} reports {@code maxSize == 10}, and so on.
 * <p>
 * The constraints are read from the descriptors of the class and of its superclasses, as the Bean Validation
 * provider sees them: a composed constraint is described through the constraints composing it, with its attribute
 * overrides applied, and only the constraints of the default group are described, as {@code validate()} validates no
 * other group. A constraint on a getter without a backing field, such as a cross field {@code @AssertTrue}, is
 * validated but not described, unless the {@code constraints} block constrains the property. Annotations without
 * a GORM equivalent (such as {@code @Past}, {@code @Digits} or custom constraints) are left out.
 *
 * @since 8.0.1
 */
@Slf4j
@CompileStatic
class JakartaConstraintsTranslator {

    private static final Set<Class<?>> DEFAULT_GROUP = Collections.<Class<?>> singleton(Default)

    /**
     * The descriptor of the class
     */
    final BeanDescriptor beanDescriptor

    /**
     * The constraints of the default group by property name, the properties without any left out
     */
    final Map<String, List<ConstraintDescriptor<?>>> constraintDescriptors

    /**
     * The constraints of every group by property name
     */
    private final Map<String, List<DeclaredConstraint>> declaredConstraints

    /**
     * @param validator The Bean Validation validator to read the descriptors of the class and its superclasses from
     * @param beanClass The class
     */
    JakartaConstraintsTranslator(Validator validator, Class<?> beanClass) {
        this.beanDescriptor = validator.getConstraintsForClass(beanClass)
        this.declaredConstraints = findDeclaredConstraints(validator, beanClass)
        this.constraintDescriptors = Collections.unmodifiableMap(findValidatedConstraints(DEFAULT_GROUP))
    }

    /**
     * @return The described class
     */
    Class<?> getBeanClass() {
        return beanDescriptor.elementClass
    }

    /**
     * Reads the constraints each class of the hierarchy declares, with the groups the default group stands for in
     * that class. As the provider validates them, the first class from the described class up to redefine the
     * default group with a {@link GroupSequence} applies its sequence to its own constraints and to the ones of its
     * superclasses, whereas the classes below it validate theirs in the {@link Default} group only.
     */
    private static Map<String, List<DeclaredConstraint>> findDeclaredConstraints(Validator validator, Class<?> beanClass) {
        List<Class<?>> hierarchy = hierarchyOf(beanClass)
        int redefining = hierarchy.findIndexOf { Class<?> type -> !type.isInterface() && type.getAnnotation(GroupSequence) != null }
        Set<Class<?>> sequenced = redefining < 0 ? DEFAULT_GROUP : sequencedDefaultGroupsOf(hierarchy.get(redefining))
        Map<String, List<DeclaredConstraint>> result = new TreeMap<>()
        for (int i = 0; i < hierarchy.size(); i++) {
            Set<Class<?>> defaultGroups = redefining >= 0 && i >= redefining ? sequenced : DEFAULT_GROUP
            for (PropertyDescriptor property in validator.getConstraintsForClass(hierarchy.get(i)).constrainedProperties) {
                Set<ConstraintDescriptor<?>> declared = property.findConstraints().lookingAt(Scope.LOCAL_ELEMENT).constraintDescriptors
                for (ConstraintDescriptor<?> descriptor in declared) {
                    result.computeIfAbsent(property.propertyName) { String name -> new ArrayList<DeclaredConstraint>() }
                            .add(new DeclaredConstraint(descriptor, defaultGroups))
                }
            }
        }
        return result
    }

    /**
     * The class, its interfaces, then its superclass with its interfaces and so on, each type once, in the order
     * the provider visits them.
     */
    private static List<Class<?>> hierarchyOf(Class<?> beanClass) {
        List<Class<?>> hierarchy = new ArrayList<>()
        addHierarchy(beanClass, hierarchy)
        return hierarchy
    }

    private static void addHierarchy(Class<?> type, List<Class<?>> hierarchy) {
        for (Class<?> current = type; current != null; current = current.superclass) {
            if (hierarchy.contains(current)) {
                return
            }
            hierarchy.add(current)
            for (Class<?> implemented in current.interfaces) {
                addHierarchy(implemented, hierarchy)
            }
        }
    }

    /**
     * The groups the default group of a class stands for when the class redefines it: the {@link Default} group, and
     * the groups of the sequence, in which the class itself stands for the {@link Default} group.
     */
    private static Set<Class<?>> sequencedDefaultGroupsOf(Class<?> redefining) {
        Set<Class<?>> groups = new HashSet<>()
        groups.add(Default)
        Set<Class<?>> visitedSequences = new HashSet<>()
        visitedSequences.add(redefining)
        addSequencedGroups(redefining.getAnnotation(GroupSequence).value(), groups, visitedSequences)
        return Collections.unmodifiableSet(groups)
    }

    /**
     * Adds the groups validated when the given ones are, as the provider validates them: a group brings the groups
     * it extends, and a sequence its own groups.
     */
    private static void addSequencedGroups(Class<?>[] sequence, Set<Class<?>> groups, Set<Class<?>> visitedSequences) {
        for (Class<?> group in sequence) {
            GroupSequence nested = group.getAnnotation(GroupSequence)
            if (nested == null) {
                addWithInheritedGroups(group, groups)
            }
            else if (visitedSequences.add(group)) {
                addSequencedGroups(nested.value(), groups, visitedSequences)
            }
        }
    }

    private static void addWithInheritedGroups(Class<?> group, Set<Class<?>> groups) {
        if (groups.add(group)) {
            for (Class<?> inherited in group.interfaces) {
                addWithInheritedGroups(inherited, groups)
            }
        }
    }

    /**
     * The constraints validated when the given groups are, by property name, ordered so that the description is the
     * same on every run when two of them describe the same GORM constraint, as the provider reports them as a set.
     */
    private Map<String, List<ConstraintDescriptor<?>>> findValidatedConstraints(Collection<Class<?>> groups) {
        Set<Class<?>> requested = new HashSet<>()
        addSequencedGroups(groups.toArray(new Class<?>[0]), requested, new HashSet<Class<?>>())
        Map<String, List<ConstraintDescriptor<?>>> result = new LinkedHashMap<>()
        declaredConstraints.each { String propertyName, List<DeclaredConstraint> declared ->
            List<ConstraintDescriptor<?>> validated = new ArrayList<>()
            for (DeclaredConstraint constraint in declared) {
                if (constraint.isValidatedIn(requested)) {
                    validated.add(constraint.descriptor)
                }
            }
            if (validated) {
                result.put(propertyName, validated.toSorted { ConstraintDescriptor<?> a, ConstraintDescriptor<?> b ->
                    a.annotation.toString() <=> b.annotation.toString()
                })
            }
        }
        return result
    }

    /**
     * The properties whose nullability a constraint of the default group declares ({@code @NotNull},
     * {@code @NotBlank}, {@code @NotEmpty} or {@code @Null}, directly or through a composed constraint).
     *
     * @return The property names
     */
    Set<String> getNullabilityConstrainedProperties() {
        return nullabilityConstrainedPropertiesOf(constraintDescriptors)
    }

    /**
     * The properties whose nullability a constraint validated in the given groups declares, as
     * {@link #getNullabilityConstrainedProperties()} for the default group.
     *
     * @param groups The groups, the default group when there are none
     * @return The property names
     */
    Set<String> findNullabilityConstrainedProperties(Class<?>... groups) {
        if (isDefaultGroup(groups)) {
            return nullabilityConstrainedProperties
        }
        return nullabilityConstrainedPropertiesOf(findValidatedConstraints(Arrays.asList(groups)))
    }

    private static boolean isDefaultGroup(Class<?>[] groups) {
        return groups.length == 0 || (groups.length == 1 && groups[0] == Default)
    }

    private static Set<String> nullabilityConstrainedPropertiesOf(Map<String, List<ConstraintDescriptor<?>>> descriptorsByProperty) {
        Set<String> result = new LinkedHashSet<>()
        descriptorsByProperty.each { String propertyName, List<ConstraintDescriptor<?>> descriptors ->
            if (descriptors.any { ConstraintDescriptor<?> descriptor -> constrainsNullability(descriptor) }) {
                result.add(propertyName)
            }
        }
        return result
    }

    private static boolean constrainsNullability(ConstraintDescriptor<?> descriptor) {
        Annotation annotation = descriptor.annotation
        if (annotation instanceof NotNull || annotation instanceof NotBlank || annotation instanceof NotEmpty || annotation instanceof Null) {
            return true
        }
        return descriptor.composingConstraints.any { ConstraintDescriptor<?> composing -> constrainsNullability(composing) }
    }

    /**
     * Leaves the nullability of the {@link #findNullabilityConstrainedProperties properties Bean Validation checks
     * for null} in the given groups to it by relaxing their GORM {@code nullable} constraint, so that a {@code null}
     * value is reported once rather than by both validators. The constraints are modified in place.
     *
     * @param constraints The evaluated GORM constraints of the class
     * @param groups The groups the class is validated in, the default group when there are none
     */
    void delegateNullabilityToBeanValidation(Map<String, ConstrainedProperty> constraints, Class<?>... groups) {
        for (String propertyName in findNullabilityConstrainedProperties(groups)) {
            ConstrainedProperty constrained = constraints.get(propertyName)
            if (constrained != null && constrained.hasAppliedConstraint(ConstrainedProperty.NULLABLE_CONSTRAINT)) {
                constrained.applyConstraint(ConstrainedProperty.NULLABLE_CONSTRAINT, true)
            }
        }
    }

    /**
     * Describes the Bean Validation constraints of the class as GORM constraints, on top of a copy of the given
     * constraints. The given constraints are not modified, as they are the ones the GORM validator validates with.
     *
     * @param constraints The GORM constraints of the class, typically evaluated from its {@code constraints} block
     * @param registry The registry to build the constraints with
     * @return A new map with the merged constraints, or the given map when the class has no constraints to describe
     */
    Map<String, ConstrainedProperty> translate(Map<String, ConstrainedProperty> constraints, ConstraintRegistry registry) {
        if (constraintDescriptors.isEmpty()) {
            return constraints
        }
        ConstraintRegistry targetRegistry = registry ?: new DefaultConstraintRegistry(new StaticMessageSource())
        Map<String, ConstrainedProperty> merged = new LinkedHashMap<>(constraints ?: Collections.<String, ConstrainedProperty> emptyMap())
        Class<?> beanClass = this.beanClass
        constraintDescriptors.each { String propertyName, List<ConstraintDescriptor<?>> descriptors ->
            ConstrainedProperty existing = constraints?.get(propertyName)
            DefaultConstrainedProperty constrained
            if (existing != null) {
                constrained = copy(existing, targetRegistry)
            }
            else if (ReflectionUtils.findField(beanClass, propertyName) == null) {
                // a getter without a backing field, such as a cross field @AssertTrue, is not a property to describe
                return
            }
            else {
                Class<?> propertyType = ClassPropertyFetcher.getPropertyType(beanClass, propertyName)
                if (propertyType == null) {
                    return
                }
                constrained = new DefaultConstrainedProperty(beanClass, propertyName, propertyType, targetRegistry)
                constrained.order = merged.size() + 1
            }
            for (ConstraintDescriptor<?> descriptor in descriptors) {
                applyConstraint(constrained, descriptor)
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
     * Applies the GORM constraints describing the constraint. Bounds tighten the ones already applied rather than
     * replace them. A composed constraint without an equivalent of its own is described through the constraints
     * composing it, with the attributes it overrides applied.
     *
     * @param constrained The constrained property to describe the constraint on
     * @param descriptor The constraint
     */
    void applyConstraint(DefaultConstrainedProperty constrained, ConstraintDescriptor<?> descriptor) {
        Annotation annotation = descriptor.annotation
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
                if (isIntegral(constrained.propertyType)) {
                    // the smallest whole number the bound admits
                    tightenMin(constrained, decimalMin.inclusive()
                            ? min.setScale(0, RoundingMode.CEILING)
                            : min.setScale(0, RoundingMode.FLOOR).add(BigDecimal.ONE))
                }
                else if (decimalMin.inclusive()) {
                    tightenMin(constrained, min)
                }
                break
            case DecimalMax:
                DecimalMax decimalMax = (DecimalMax) annotation
                BigDecimal max = new BigDecimal(decimalMax.value())
                if (isIntegral(constrained.propertyType)) {
                    // the largest whole number the bound admits
                    tightenMax(constrained, decimalMax.inclusive()
                            ? max.setScale(0, RoundingMode.FLOOR)
                            : max.setScale(0, RoundingMode.CEILING).subtract(BigDecimal.ONE))
                }
                else if (decimalMax.inclusive()) {
                    tightenMax(constrained, max)
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
                if (!applyHibernateValidatorConstraint(constrained, descriptor)) {
                    for (ConstraintDescriptor<?> composing in descriptor.composingConstraints) {
                        applyConstraint(constrained, composing)
                    }
                }
        }
    }

    /**
     * The Hibernate Validator specific constraints with a GORM equivalent, matched by name so that no provider is
     * required. {@code @URL} is matched before the empty {@code @Pattern} composing it would be described.
     */
    private boolean applyHibernateValidatorConstraint(DefaultConstrainedProperty constrained, ConstraintDescriptor<?> descriptor) {
        Map<String, Object> attributes = descriptor.attributes
        switch (descriptor.annotation.annotationType().name) {
            case 'org.hibernate.validator.constraints.Length':
                int min = (int) attributes.get('min')
                int max = (int) attributes.get('max')
                if (min > 0) {
                    tightenMinSize(constrained, min)
                }
                if (max < Integer.MAX_VALUE) {
                    tightenMaxSize(constrained, max)
                }
                return true
            case 'org.hibernate.validator.constraints.Range':
                tightenMin(constrained, (long) attributes.get('min'))
                tightenMax(constrained, (long) attributes.get('max'))
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
            log.warn('Unable to describe a Bean Validation constraint on [{}.{}] with the [{}] constraint: {}',
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

    /**
     * A constraint, with the groups the default group stands for in the class declaring it
     */
    @CompileStatic
    private static class DeclaredConstraint {

        final ConstraintDescriptor<?> descriptor
        final Set<Class<?>> defaultGroups

        DeclaredConstraint(ConstraintDescriptor<?> descriptor, Set<Class<?>> defaultGroups) {
            this.descriptor = descriptor
            this.defaultGroups = defaultGroups
        }

        /**
         * @param groups The groups validated, with the groups they extend and the groups of their sequences
         * @return Whether the constraint is validated in them
         */
        boolean isValidatedIn(Set<Class<?>> groups) {
            Set<Class<?>> validated = groups
            if (groups.contains(Default)) {
                validated = new HashSet<>(groups)
                validated.addAll(defaultGroups)
            }
            return !Collections.disjoint(descriptor.groups, validated)
        }
    }
}

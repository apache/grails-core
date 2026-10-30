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

package grails.gorm.validation

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.springframework.context.MessageSource
import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.SmartValidator

import org.grails.datastore.gorm.support.BeforeValidateHelper
import org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.reflect.EntityReflector

/**
 * A Validator that validates a {@link org.grails.datastore.mapping.model.PersistentEntity} against known constraints.
 * <p>
 * As a {@link SmartValidator}, it validates an object with validation hints, which it passes to the validator of
 * each object validation cascades to, see {@link #getAssociationValidationHints}. An associated instance is
 * validated once with each distinct set of hints it is reached with, whatever it compares equal to: two instances
 * are both validated, while a cycle of associations ends at the instance it leads back to, whatever hints it is
 * reached with. An instance of a subclass of the entity that is an entity of its own is validated by the validator
 * of that entity.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class PersistentEntityValidator implements CascadingValidator, ConstrainedEntity, SmartValidator {

    private static final List<String> EMBEDDED_EXCLUDES = Arrays.asList(
                                                            GormProperties.IDENTITY,
                                                            GormProperties.VERSION)

    /**
     * No validation hints: an object is validated as {@link #validate} validates it
     *
     * @since 9.0.0
     */
    protected static final Object[] NO_VALIDATION_HINTS = new Object[0]

    /**
     * The cascade in progress on the thread: the hints of the object whose associations validation is cascading to
     */
    private static final ThreadLocal<Cascade> CASCADE = new ThreadLocal<>()

    final PersistentEntity entity
    final EntityReflector entityReflector
    final MessageSource messageSource
    final Class targetClass
    final Map<String, ConstrainedProperty> constrainedProperties
    final BeforeValidateHelper validateHelper = new BeforeValidateHelper()

    protected final ProxyHandler proxyHandler

    PersistentEntityValidator(PersistentEntity entity, MessageSource messageSource, ConstraintsEvaluator constraintsEvaluator) {
        this.entity = entity
        this.messageSource = messageSource
        this.targetClass = entity.javaClass
        def mappingContext = entity.getMappingContext()
        this.entityReflector = mappingContext.getEntityReflector(entity)
        this.proxyHandler = mappingContext.getProxyHandler()

        def evaluated = constraintsEvaluator.evaluate(targetClass)
        this.constrainedProperties = Collections.unmodifiableMap(evaluated)
        if (constrainedProperties == null) {
            throw new IllegalStateException("Constraint evaluator returned null for class: $targetClass")
        }
    }

    @Override
    void validate(Object obj, Errors errors, boolean cascade = true) {
        validate(obj, errors, cascade, NO_VALIDATION_HINTS)
    }

    /**
     * Validates the object with the given validation hints, cascading to its associations. The hints are passed to
     * {@link #getValidatedConstrainedProperties} and {@link #validateBeyondConstraints}, and to the validator of
     * each associated object as {@link #getAssociationValidationHints} converts them. A plain entity is validated
     * as without hints. For an entity annotated with Bean Validation constraints, the hints that are classes are
     * the groups to validate in.
     *
     * @param obj The object to validate
     * @param errors The errors to report the violations to
     * @param validationHints The validation hints, none to validate as {@link #validate(Object, Errors)} does
     * @since 9.0.0
     */
    @Override
    void validate(Object obj, Errors errors, Object... validationHints) {
        validate(obj, errors, true, validationHints ?: NO_VALIDATION_HINTS)
    }

    /**
     * Validates the object with the given validation hints, cascading to its associations when asked to. A proxy
     * of the entity is validated as the instance it stands for, and so is a proxy of an associated object. An
     * instance of a subclass of the entity that is an entity of its own is handed to the validator of that entity,
     * with the same hints, as {@link #supports} accepts the subclasses of the entity.
     *
     * @param obj The object to validate
     * @param errors The errors to report the violations to
     * @param cascade Whether to cascade to the associations of the object
     * @param hints The validation hints, {@link #NO_VALIDATION_HINTS} for none
     * @since 9.0.0
     */
    protected void validate(Object obj, Errors errors, boolean cascade, Object[] hints) {
        Object target = unwrap(obj)
        if (target == null || !targetClass.isInstance(target)) {
            throw new IllegalArgumentException("Argument [$obj] is not an instance of [$targetClass] which this validator is configured for")
        }

        PersistentEntityValidator subclassValidator = findSubclassEntityValidator(target)
        if (subclassValidator != null) {
            subclassValidator.validate(target, errors, cascade, hints)
            return
        }

        def validatedObjects = new HashSet()
        validatedObjects.add(new ValidatedObject(target, hints))

        // a validation started while another one cascades, such as by a custom validator, starts with its own hints
        // and its own path, which a cycle of associations ends at
        Cascade enclosing = CASCADE.get()
        CASCADE.set(new Cascade(target, hints, hints, null, null))
        try {
            validateConstraints(target, entity, errors, entityReflector, getValidatedConstrainedProperties(target, hints), null, true)
            validateAssociationsAndBeyond(target, errors, cascade, hints, validatedObjects)
        }
        finally {
            restoreCascade(enclosing)
        }
    }

    /**
     * The validator of the entity of the class of the instance, when that class is a subclass of the class of the
     * entity of this validator and an entity of its own, with a validator of this kind of its own, null when the
     * instance is to be validated by this validator.
     */
    private PersistentEntityValidator findSubclassEntityValidator(Object instance) {
        Class<?> type = instance.getClass()
        if (type == targetClass) {
            return null
        }
        MappingContext mappingContext = entity.mappingContext
        PersistentEntity subclassEntity = mappingContext.getPersistentEntity(type.name)
        if (subclassEntity == null || subclassEntity.is(entity)) {
            return null
        }
        def validator = mappingContext.getEntityValidator(subclassEntity)
        if (validator instanceof PersistentEntityValidator && !validator.is(this)) {
            return (PersistentEntityValidator) validator
        }
        return null
    }

    /**
     * The constraints this validator validates an object with: the ones evaluated from the {@code constraints} block
     * of the entity and the defaults. {@link #getConstrainedProperties()} exposes the same constraints, unless a
     * subclass also describes rules it validates by other means in them, such as Bean Validation annotations.
     *
     * @param obj The object to validate, the instance a proxy stands for, for a subclass whose rules depend on the
     * instance, such as the groups a Bean Validation group sequence provider chooses for it
     * @param hints The validation hints the object is validated with, {@link #NO_VALIDATION_HINTS} for the object
     * {@link #validate} is called with, see {@link #getAssociationValidationHints}
     * @return The constraints to validate
     * @since 9.0.0
     */
    protected Map<String, ConstrainedProperty> getValidatedConstrainedProperties(Object obj, Object[] hints) {
        return this.constrainedProperties
    }

    /**
     * Validates the rules of the object beyond its constraints, such as the Bean Validation annotations of its
     * class. Called after the constraints and the associations of the object: for the object being validated by
     * {@link #validate}, and for each associated object validation cascades to by the validator of its entity, with
     * the nested path of the errors set to the association. Does nothing by default.
     *
     * @param obj The object to validate
     * @param errors The errors to report the violations to
     * @param hints The validation hints the object is validated with, {@link #NO_VALIDATION_HINTS} for the object
     * {@link #validate} is called with, see {@link #getAssociationValidationHints}
     * @since 9.0.0
     */
    protected void validateBeyondConstraints(Object obj, Errors errors, Object[] hints) {
        // the constraints are the only rules of a plain entity
    }

    /**
     * Validates the object beyond its constraints, once they are: cascades validation to its associations when
     * asked to, with {@link #cascadeToAssociations}, then validates its rules beyond the constraints with
     * {@link #validateBeyondConstraints}. Called for the object being validated by {@link #validate}, and for each
     * associated object validation cascades to by the validator of its entity, with the nested path of the errors
     * set to the association. A subclass validating in steps, such as the groups of a Bean Validation group
     * sequence, overrides it to cascade and validate step by step.
     *
     * @param obj The object to validate
     * @param errors The errors to report the violations to
     * @param cascade Whether to cascade to the associations of the object
     * @param hints The validation hints the object is validated with, {@link #NO_VALIDATION_HINTS} for the object
     * {@link #validate} is called with, see {@link #getAssociationValidationHints}
     * @param validatedObjects The objects this validation has reached, to pass on to the cascade
     * @since 9.0.0
     */
    protected void validateAssociationsAndBeyond(Object obj, Errors errors, boolean cascade, Object[] hints, Set validatedObjects) {
        if (cascade) {
            cascadeToAssociations(obj, entity, errors, entityReflector, hints, validatedObjects)
        }
        validateBeyondConstraints(obj, errors, hints)
    }

    /**
     * Cascades validation to the associations of the object, with the given validation hints as the hints of the
     * object, which {@link #getAssociationValidationHints} converts for each association. The inverse side of the
     * association the object was reached through is left out.
     *
     * @param obj The object whose associations to cascade to
     * @param objEntity The entity of the object
     * @param errors The errors to report the violations to
     * @param reflector The reflector of the entity
     * @param hints The validation hints the object is validated with
     * @param validatedObjects The objects this validation has reached
     * @since 9.0.0
     */
    protected void cascadeToAssociations(Object obj, PersistentEntity objEntity, Errors errors, EntityReflector reflector, Object[] hints, Set validatedObjects) {
        Cascade current = CASCADE.get()
        Association reachedThrough = current?.association
        CASCADE.set(new Cascade(obj, hints, current == null ? hints : current.allHints, reachedThrough, current))
        try {
            for (PersistentProperty pp in objEntity.persistentProperties) {
                if (!(pp instanceof Association)) {
                    continue
                }
                // Don't continue cascade if the the other side is equal to avoid stack overflow
                if (reachedThrough != null && reachedThrough.isBidirectional() && pp == reachedThrough.inverseSide) {
                    continue
                }
                cascadeToAssociativeProperty(obj, errors, reflector, (Association) pp, validatedObjects)
            }
        }
        finally {
            restoreCascade(current)
        }
    }

    /**
     * The validation hints, such as Bean Validation groups, to validate an object associated through an association
     * of the entity of this validator with, given the hints the object owning the association is validated with.
     * The validator of the associated entity receives them in {@link #getValidatedConstrainedProperties} and
     * {@link #validateBeyondConstraints}, and they are the hints of the associated object when validation cascades
     * on to its own associations. None by default, so an associated object is validated as {@link #validate}
     * validates it.
     *
     * @param association The association, a property of the entity of this validator
     * @param hints The validation hints the object owning the association is validated with
     * @return The validation hints of the associated object
     * @since 9.0.0
     */
    protected Object[] getAssociationValidationHints(Association association, Object[] hints) {
        return NO_VALIDATION_HINTS
    }

    private static void restoreCascade(Cascade cascade) {
        if (cascade == null) {
            CASCADE.remove()
        }
        else {
            CASCADE.set(cascade)
        }
    }

    private Object unwrap(Object obj) {
        if (obj != null && proxyHandler != null && proxyHandler.isProxy(obj)) {
            return proxyHandler.unwrap(obj)
        }
        return obj
    }

    /**
     * Validates the constraints of the object, the persistent properties of its entity first, in their order.
     *
     * @param includeTransient Whether to validate the constrained properties that are not persistent properties
     */
    private static void validateConstraints(Object obj, PersistentEntity objEntity, Errors errors, EntityReflector reflector,
            Map<String, ConstrainedProperty> constrainedProperties, Association reachedThrough, boolean includeTransient) {
        Set<String> constrainedPropertyNames = new HashSet<>(constrainedProperties.keySet())
        for (PersistentProperty pp in objEntity.persistentProperties) {
            String propertyName = pp.name
            if (reachedThrough != null && reachedThrough.isEmbedded() && EMBEDDED_EXCLUDES.contains(propertyName)) {
                continue
            }
            ConstrainedProperty constrainedProperty = constrainedProperties.get(propertyName)
            if (constrainedProperty != null) {
                validatePropertyWithConstraint(obj, propertyName, reflector, errors, constrainedProperty, pp)
            }
            constrainedPropertyNames.remove(propertyName)
        }
        if (includeTransient) {
            for (String remainingProperty in constrainedPropertyNames) {
                ConstrainedProperty constrainedProperty = constrainedProperties.get(remainingProperty)
                if (remainingProperty != null) {
                    validatePropertyWithConstraint(obj, remainingProperty, reflector, errors, constrainedProperty, null)
                }
            }
        }
    }

    /**
     * Cascades validation onto an associative property maybe a one-to-many, one-to-one or many-to-one relationship.
     *
     * @param errors The Errors instnace
     * @param bean The original bean
     * @param association The associative property
     */
    protected void cascadeToAssociativeProperty(Object parent, Errors errors, EntityReflector reflector, Association association, Set validatedObjects) {
        String propertyName = association.getName()
        if (errors.hasFieldErrors(propertyName)) {
            return
        }

        if (association instanceof ToOne) {
            Object associatedObject = reflector.getProperty(parent, propertyName)

            if (associatedObject != null && proxyHandler?.isInitialized(associatedObject)) {
                if (association.doesCascadeValidate(associatedObject)) {
                    cascadeValidationToOne(parent, propertyName, (ToOne) association, errors, reflector, associatedObject, null, validatedObjects)
                }
                else {
                    Errors existingErrors = retrieveErrors(associatedObject)
                    if (existingErrors != null && existingErrors.hasErrors()) {
                        for (error in existingErrors.fieldErrors) {
                            String path = "${propertyName}." + error.field
                            errors.rejectValue(path, error.code, error.arguments, error.defaultMessage)
                        }
                    }
                }
            }
        }
        else if (association instanceof ToMany) {
            if (association.doesCascadeValidate(null)) {
                cascadeValidationToMany(parent, propertyName, association, errors, reflector, validatedObjects)
            }
        }

    }

    @CompileDynamic
    protected Errors retrieveErrors(associatedObject) {
        (Errors) associatedObject.errors
    }

    /**
     * Cascades validation to a one-to-many type relationship. Normally a collection such as a List or Set
     * each element in the association will also be validated.
     *
     * @param errors The Errors instance
     * @param entityReflector The entity reflector
     * @param association An association whose isOneToMeny() method returns true
     * @param propertyName The name of the property
     */
    @SuppressWarnings('rawtypes')
    protected void cascadeValidationToMany(Object parentObject, String propertyName, Association association, Errors errors, EntityReflector entityReflector, Set validatedObjects) {

        Object collection = entityReflector.getProperty(parentObject, propertyName)
        if (collection == null || !proxyHandler?.isInitialized(collection)) {
            return
        }

        if (collection instanceof List || collection instanceof SortedSet) {
            int idx = 0
            for (Object associatedObject : ((Collection) collection)) {
                cascadeValidationToOne(parentObject, propertyName, association, errors, entityReflector, associatedObject, idx++, validatedObjects)
            }
        }
        else if (collection instanceof Collection) {
            Integer index = 0
            for (Object associatedObject : ((Collection) collection)) {
                cascadeValidationToOne(parentObject, propertyName, association, errors, entityReflector, associatedObject, index++, validatedObjects)
            }
        }
        else if (collection instanceof Map) {

            for (Object entryObject in ((Map) collection).entrySet()) {
                Map.Entry entry = (Map.Entry) entryObject
                cascadeValidationToOne(parentObject, propertyName, association, errors, entityReflector, entry.value, entry.key, validatedObjects)
            }
        }
    }

    /**
     * Cascades validation to a one-to-one or many-to-one property. A proxy is validated as the instance it stands
     * for, as the object validation starts with is. A cycle of associations ends here: an instance validation is
     * cascading from, the object validation started with or one on the path to the associated object, is not
     * validated again, whatever hints it is reached with. Any other associated object is validated with the hints
     * the validator of the owning entity gives for the association: its constraints once, with the hints given for
     * all the hints of the owner, and, once for each distinct hints it is reached with, its associations and its
     * rules beyond the constraints, through {@link #validateAssociationsAndBeyond} of its own validator.
     *
     * @param errors The Errors instance
     * @param bean The original BeanWrapper
     * @param associatedObject The associated object's current value, a proxy or the instance itself
     * @param association The GrailsDomainClassProperty instance
     * @param propertyName The name of the property
     * @param indexOrKey
     */
    @SuppressWarnings('rawtypes')
    protected void cascadeValidationToOne(Object parentObject, String propertyName, Association association, Errors errors, EntityReflector reflector, Object associatedObject, Object indexOrKey, Set validatedObjects) {
        // the instance holds the state a validator reading fields, such as Bean Validation, looks for, which a proxy
        // does not carry, and it is the one a cycle of associations has to end at however it is reached
        Object instance = unwrap(associatedObject)
        if (instance == null) {
            return
        }

        // a cycle of associations ends at an instance validation is cascading from, whatever hints it is reached with
        Cascade cascade = CASCADE.get()
        if (cascade != null && cascade.leadsBackTo(instance)) {
            return
        }

        // the hints decide what the object is validated with, so it is validated once with each distinct set of them
        Object[] ownerHints = cascade == null ? NO_VALIDATION_HINTS : cascade.hints
        Object[] ownerAllHints = cascade == null ? NO_VALIDATION_HINTS : cascade.allHints
        Object[] associatedHints = NO_VALIDATION_HINTS
        Object[] associatedAllHints = NO_VALIDATION_HINTS
        PersistentEntity owner = association.owner
        def ownerValidator = owner.is(entity) ? this : owner.mappingContext.getEntityValidator(owner)
        if (ownerValidator instanceof PersistentEntityValidator) {
            PersistentEntityValidator hinting = (PersistentEntityValidator) ownerValidator
            associatedHints = hinting.getAssociationValidationHints(association, ownerHints)
            associatedAllHints = ownerAllHints.is(ownerHints) ? associatedHints : hinting.getAssociationValidationHints(association, ownerAllHints)
        }

        if (!validatedObjects.add(new ValidatedObject(instance, associatedHints))) {
            return
        }

        PersistentEntity associatedEntity = association.getAssociatedEntity()
        if (associatedEntity == null) {
            return
        }

        // Make sure this object is eligible to cascade validation at all.
        if (!association.doesCascadeValidate(instance)) {
            return
        }

        MappingContext mappingContext = associatedEntity.getMappingContext()
        EntityReflector associatedReflector = mappingContext.getEntityReflector(associatedEntity)

        PersistentEntityValidator associatedValidator = null
        Map<String, ConstrainedProperty> associatedConstrainedProperties = Collections.<String, ConstrainedProperty>emptyMap()

        def validator = mappingContext.getEntityValidator(associatedEntity)
        if (validator instanceof PersistentEntityValidator) {
            associatedValidator = (PersistentEntityValidator) validator
            associatedConstrainedProperties = associatedValidator.getValidatedConstrainedProperties(instance, associatedAllHints)
        }

        String nestedPath = errors.getNestedPath()
        try {
            errors.setNestedPath(buildNestedPath(nestedPath, propertyName, indexOrKey))
            CASCADE.set(new Cascade(instance, associatedHints, associatedAllHints, association, cascade))

            // the constraints do not depend on the hints of a step, so they are validated once for all of them
            if (validatedObjects.add(new ValidatedConstraints(instance, associatedAllHints))) {
                // Invoke any beforeValidate callbacks on the associated object before validating
                validateHelper.invokeBeforeValidate(instance, associatedConstrainedProperties.keySet() as List<String>)
                validateConstraints(instance, associatedEntity, errors, associatedReflector, associatedConstrainedProperties, association, false)
            }

            if (associatedValidator != null) {
                associatedValidator.validateAssociationsAndBeyond(instance, errors, true, associatedHints, validatedObjects)
            }
            else {
                cascadeToAssociations(instance, associatedEntity, errors, associatedReflector, associatedHints, validatedObjects)
            }
        }
        finally {
            errors.setNestedPath(nestedPath)
            restoreCascade(cascade)
        }
    }

    private String buildNestedPath(String nestedPath, String componentName, Object indexOrKey) {
        if (indexOrKey == null) {
            // Component is neither part of a Collection nor Map.
            return nestedPath + componentName
        }

        if (indexOrKey instanceof Integer) {
            // Component is part of a Collection. Collection access string
            // e.g. path.object[1] will be appended to the nested path.
            return nestedPath + componentName + '[' + indexOrKey + ']'
        }

        // Component is part of a Map. Nested path should have a key surrounded
        // with apostrophes at the end.
        return nestedPath + componentName + "['" + indexOrKey + "']"
    }

    private static void validatePropertyWithConstraint(Object obj, String propertyName, EntityReflector reflector, Errors errors, ConstrainedProperty constrainedProperty, PersistentProperty persistentProperty) {

        int i = propertyName.lastIndexOf('.')
        String constrainedPropertyName
        if (i > -1) {
            constrainedPropertyName = propertyName.substring(i + 1, propertyName.length())
        }
        else {
            constrainedPropertyName = propertyName
        }
        FieldError fieldError = errors.getFieldError(constrainedPropertyName)
        if (fieldError == null) {
            if (persistentProperty != null) {
                constrainedProperty.validate(obj, reflector.getProperty(obj, constrainedPropertyName), errors)
            }
            else {
                if (obj instanceof GroovyObject) {
                    constrainedProperty.validate(obj, ((GroovyObject) obj).getProperty(constrainedPropertyName), errors)
                }
            }
        }
    }

    /**
     * @return Whether the class is the class of the entity or a subclass of it, such as the class of a proxy, or
     * the class of an entity extending it, whose instances {@link #validate} hands to the validator of that entity
     */
    @Override
    boolean supports(Class<?> clazz) {
        return targetClass.isAssignableFrom(clazz)
    }

    /**
     * An object whose associations validation is cascading to, with its validation hints and the cascade it was
     * reached through, whose objects lead from the object validation started with to it
     */
    private static final class Cascade {

        /**
         * The object
         */
        final Object instance

        /**
         * The hints the object is validated with in the current step
         */
        final Object[] hints

        /**
         * The hints the object is validated with in all its steps, the same as {@link #hints} for a validator
         * validating in one step
         */
        final Object[] allHints

        /**
         * The association the object was reached through, null for the object validation was started with
         */
        final Association association

        /**
         * The cascade of the object this object was reached from, null for the object validation was started with
         */
        final Cascade parent

        Cascade(Object instance, Object[] hints, Object[] allHints, Association association, Cascade parent) {
            this.instance = instance
            this.hints = hints
            this.allHints = allHints
            this.association = association
            this.parent = parent
        }

        /**
         * @return Whether the candidate is the object of this cascade or of one it was reached through, so that an
         * association to the candidate is a cycle leading back to it
         */
        boolean leadsBackTo(Object candidate) {
            for (Cascade cascade = this; cascade != null; cascade = cascade.parent) {
                if (cascade.instance.is(candidate)) {
                    return true
                }
            }
            return false
        }
    }

    /**
     * An instance validation has reached, with the validation hints it was validated with. The same instance reached
     * with the same hints is the same validated object, so a cycle of associations ends, while an instance reached
     * again with other hints is validated again with them, and another instance is validated whatever it compares
     * equal to.
     */
    private static final class ValidatedObject {

        private final Object object
        private final List<Object> hints

        ValidatedObject(Object object, Object[] hints) {
            this.object = object
            this.hints = Arrays.asList(hints)
        }

        @Override
        boolean equals(Object other) {
            if (!(other instanceof ValidatedObject)) {
                return false
            }
            ValidatedObject that = (ValidatedObject) other
            return object.is(that.object) && hints == that.hints
        }

        @Override
        int hashCode() {
            return 31 * System.identityHashCode(object) + hints.hashCode()
        }
    }

    /**
     * An instance whose constraints validation has validated, with the hints it was validated with in all its steps
     */
    private static final class ValidatedConstraints {

        private final Object object
        private final List<Object> hints

        ValidatedConstraints(Object object, Object[] hints) {
            this.object = object
            this.hints = Arrays.asList(hints)
        }

        @Override
        boolean equals(Object other) {
            if (!(other instanceof ValidatedConstraints)) {
                return false
            }
            ValidatedConstraints that = (ValidatedConstraints) other
            return object.is(that.object) && hints == that.hints
        }

        @Override
        int hashCode() {
            return 31 * System.identityHashCode(object) + hints.hashCode()
        }
    }
}

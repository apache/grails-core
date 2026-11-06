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

import java.util.function.Predicate

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.springframework.context.MessageSource
import org.springframework.validation.AbstractBindingResult
import org.springframework.validation.BindingResult
import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError

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
import org.grails.datastore.mapping.validation.ValidationErrors

/**
 * A Validator that validates a {@link org.grails.datastore.mapping.model.PersistentEntity} against known constraints
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class PersistentEntityValidator implements CascadingValidator, ConstrainedEntity {

    private static final List<String> EMBEDDED_EXCLUDES = Arrays.asList(
                                                            GormProperties.IDENTITY,
                                                            GormProperties.VERSION)

    // The listener that validate(Object, Errors, boolean, Predicate) hands to the validation it starts
    private static final ThreadLocal<Predicate<Object>> CASCADE_LISTENER = new ThreadLocal<>()

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

    /**
     * Validates the given object with a listener for the associated objects that the validation cascades to. The
     * listener is called with each of them once the beforeValidate callback of that object has run and before its
     * constraints are checked, and returns whether it has changed the object. An object that it has left alone is
     * passed to it again when every associated object has been validated, since a callback that ran in between may
     * have changed that object. When the listener changes it then, its constraints are checked again, and the errors
     * that they recorded the first time are not reported.
     *
     * <p>Only this validation calls the listener. A validation that runs while this one does, from a callback for
     * example, calls the listener it is given itself, or none.</p>
     *
     * @param obj The object to validate
     * @param errors The errors
     * @param cascade Whether the validation cascades to the associated objects
     * @param cascadeListener The listener, which may be null
     * @since 7.0.18
     */
    void validate(Object obj, Errors errors, boolean cascade, Predicate<Object> cascadeListener) {
        CASCADE_LISTENER.set(cascadeListener)
        try {
            validate(obj, errors, cascade)
        }
        finally {
            CASCADE_LISTENER.remove()
        }
    }

    @Override
    void validate(Object obj, Errors errors, boolean cascade = true) {
        // Taken before anything else runs, so that a validation that this one runs does not see it
        Predicate<Object> cascadeListener = CASCADE_LISTENER.get()
        CASCADE_LISTENER.remove()

        if (obj == null || !targetClass.isInstance(obj)) {
            throw new IllegalArgumentException("Argument [$obj] is not an instance of [$targetClass] which this validator is configured for")
        }

        Map<String, ConstrainedProperty> constrainedProperties = this.constrainedProperties
        Set<String> constrainedPropertyNames = new HashSet<>(constrainedProperties.keySet())

        ValidatedObjects validatedObjects = new ValidatedObjects(cascadeListener)
        validatedObjects.add(obj)

        // An error cannot be taken out of the errors once it is recorded, and the errors of an object that the
        // listener changes when it is passed again no longer hold. A validation with a listener therefore records
        // its errors in a copy, and adds those that hold in the end to the given errors.
        boolean replaceable = cascade && cascadeListener != null && errors instanceof BindingResult
        Errors recorded = replaceable ? copyOf((BindingResult) errors, Collections.<ObjectError>emptySet()) : errors
        int givenErrorCount = recorded.getErrorCount()

        for (PersistentProperty pp in entity.persistentProperties) {
            def propertyName = pp.name

            ConstrainedProperty constrainedProperty = constrainedProperties.get(propertyName)

            if (constrainedProperty != null) {
                validatePropertyWithConstraint(obj, propertyName, entityReflector, recorded, constrainedProperty, pp)
            }

            if (pp instanceof Association) {
                Association association = (Association) pp
                if (cascade) {
                    cascadeToAssociativeProperty(obj, recorded, entityReflector, association, validatedObjects)
                }
            }

            constrainedPropertyNames.remove(propertyName)
        }

        for (String remainingProperty in constrainedPropertyNames) {
            ConstrainedProperty constrainedProperty = constrainedProperties.get(remainingProperty)
            if (remainingProperty != null) {
                validatePropertyWithConstraint(obj, remainingProperty, entityReflector, recorded, constrainedProperty, null)
            }
        }

        recorded = offerAgain(validatedObjects, recorded)

        if (replaceable) {
            List<ObjectError> all = recorded.getAllErrors()
            for (ObjectError error : all.subList(givenErrorCount, all.size())) {
                ((BindingResult) errors).addError(error)
            }
        }
    }

    // A callback that ran after the listener was called with an object may have changed that object. The listener
    // is therefore called again with the objects it has left alone, until it leaves them all alone. The constraints
    // of an object that it changes now are checked again, in place of the first check: the errors that they
    // recorded then are left out of the errors that this method returns.
    private Errors offerAgain(ValidatedObjects validatedObjects, Errors errors) {
        List<LeftAlone> leftAlone = validatedObjects.leftAlone
        Errors recorded = errors
        boolean changed = !leftAlone.isEmpty()
        while (changed) {
            changed = false
            // From the last one, so that an object comes before the object it belongs to
            ListIterator<LeftAlone> candidates = leftAlone.listIterator(leftAlone.size())
            while (candidates.hasPrevious()) {
                LeftAlone candidate = candidates.previous()
                if (validatedObjects.cascadeListener.test(candidate.object)) {
                    candidates.remove()
                    changed = true
                    if (recorded instanceof BindingResult && !candidate.errors.isEmpty()) {
                        recorded = copyOf((BindingResult) recorded, candidate.errors)
                    }
                    String nestedPath = recorded.getNestedPath()
                    try {
                        recorded.setNestedPath(candidate.nestedPath)
                        validateConstraints(candidate.object, candidate.association, recorded)
                    }
                    finally {
                        recorded.setNestedPath(nestedPath)
                    }
                }
            }
        }
        recorded
    }

    // The errors of the same object under the same nested path, without the given ones
    private static BindingResult copyOf(BindingResult errors, Set<ObjectError> without) {
        ValidationErrors copy = new ValidationErrors(errors.getTarget(), errors.getObjectName())
        if (errors instanceof AbstractBindingResult) {
            copy.setMessageCodesResolver(((AbstractBindingResult) errors).getMessageCodesResolver())
        }
        for (ObjectError error : errors.getAllErrors()) {
            if (!without.contains(error)) {
                copy.addError(error)
            }
        }
        copy.setNestedPath(errors.getNestedPath())
        copy
    }

    // Checks the constraints of an associated object without cascading, under the nested path of that object
    private void validateConstraints(Object associatedObject, Association association, Errors errors) {
        PersistentEntity associatedEntity = association.getAssociatedEntity()
        MappingContext mappingContext = associatedEntity.getMappingContext()
        def validator = mappingContext.getEntityValidator(associatedEntity)
        if (!(validator instanceof PersistentEntityValidator)) {
            return
        }
        Map<String, ConstrainedProperty> constrained = ((PersistentEntityValidator) validator).getConstrainedProperties()
        EntityReflector reflector = mappingContext.getEntityReflector(associatedEntity)
        for (PersistentProperty property : associatedEntity.getPersistentProperties()) {
            String name = property.getName()
            ConstrainedProperty constrainedProperty = constrained.get(name)
            if (constrainedProperty != null && !(association.isEmbedded() && EMBEDDED_EXCLUDES.contains(name))) {
                validatePropertyWithConstraint(associatedObject, errors.getNestedPath() + name, reflector, errors, constrainedProperty, property)
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
     * Cascades validation to a one-to-one or many-to-one property.
     *
     * @param errors The Errors instance
     * @param bean The original BeanWrapper
     * @param associatedObject The associated object's current value
     * @param association The GrailsDomainClassProperty instance
     * @param propertyName The name of the property
     * @param indexOrKey
     */
    @SuppressWarnings('rawtypes')
    protected void cascadeValidationToOne(Object parentObject, String propertyName, Association association, Errors errors, EntityReflector reflector, Object associatedObject, Object indexOrKey, Set validatedObjects) {
        if (associatedObject == null) {
            return
        }

        if (validatedObjects.contains(associatedObject)) {
            return
        }

        validatedObjects.add(associatedObject)

        PersistentEntity associatedEntity = association.getAssociatedEntity()
        if (associatedEntity == null) {
            return
        }

        // Make sure this object is eligible to cascade validation at all.
        if (!association.doesCascadeValidate(associatedObject)) {
            return
        }

        MappingContext mappingContext = associatedEntity.getMappingContext()
        EntityReflector associatedReflector = mappingContext.getEntityReflector(associatedEntity)

        Association otherSide = null
        if (association.isBidirectional()) {
            otherSide = association.getInverseSide()
        }

        Map associatedConstrainedProperties

        def validator = mappingContext.getEntityValidator(associatedEntity)
        if (validator instanceof PersistentEntityValidator) {
            associatedConstrainedProperties = ((PersistentEntityValidator) validator).getConstrainedProperties()
        }
        else {
            associatedConstrainedProperties = Collections.<String, ConstrainedProperty>emptyMap()
        }

        // Invoke any beforeValidate callbacks on the associated object before validating
        validateHelper.invokeBeforeValidate(associatedObject, associatedConstrainedProperties.keySet() as List<String>)

        List<PersistentProperty> associatedPersistentProperties = associatedEntity.getPersistentProperties()
        String nestedPath = errors.getNestedPath()
        String associatedPath = buildNestedPath(nestedPath, propertyName, indexOrKey)

        // Tell the listener of this validation, which may complete the associated object before it is validated
        LeftAlone leftAlone = validatedObjects instanceof ValidatedObjects ?
                ((ValidatedObjects) validatedObjects).offer(associatedObject, association, associatedPath) : null

        try {
            errors.setNestedPath(associatedPath)

            for (PersistentProperty associatedPersistentProperty : associatedPersistentProperties) {
                if (association.isEmbedded() && EMBEDDED_EXCLUDES.contains(associatedPersistentProperty.getName())) {
                    continue
                }

                String associatedPropertyName = associatedPersistentProperty.getName()
                if (associatedConstrainedProperties.containsKey(associatedPropertyName)) {
                    ConstrainedProperty associatedConstrainedProperty = associatedConstrainedProperties.get(associatedPropertyName)
                    int errorCount = errors.getErrorCount()
                    validatePropertyWithConstraint(associatedObject, errors.getNestedPath() + associatedPropertyName, associatedReflector, errors, associatedConstrainedProperty, associatedPersistentProperty)
                    if (leftAlone != null) {
                        leftAlone.addErrorsSince(errors, errorCount)
                    }
                }

                // Don't continue cascade if the the other side is equal to avoid stack overflow
                if (associatedPersistentProperty == otherSide) {
                    continue
                }

                if (associatedPersistentProperty instanceof Association) {
                    if (association.isBidirectional() && associatedPersistentProperty == association.inverseSide) {
                        // If this property is the inverse side of the currently processed association then
                        // we don't want to process it
                        continue
                    }

                    cascadeToAssociativeProperty(
                        associatedObject,
                        errors,
                        associatedReflector,
                        (Association) associatedPersistentProperty,
                        validatedObjects)
                }
            }
        }
        finally {
            errors.setNestedPath(nestedPath)
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

    private void validatePropertyWithConstraint(Object obj, String propertyName, EntityReflector reflector, Errors errors, ConstrainedProperty constrainedProperty, PersistentProperty persistentProperty) {

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
    @Override
    boolean supports(Class<?> clazz) {
        return targetClass.is(clazz)
    }

    /**
     * The objects that a validation has cascaded to, which the cascade methods pass on as their set of validated
     * objects, with the listener of that validation and the objects that it has left alone.
     */
    @CompileStatic
    private static class ValidatedObjects extends HashSet<Object> {

        private static final long serialVersionUID = 1L

        final transient Predicate<Object> cascadeListener
        final transient List<LeftAlone> leftAlone = []

        ValidatedObjects(Predicate<Object> cascadeListener) {
            this.cascadeListener = cascadeListener
        }

        // Returns the record of the object when the listener has left it alone, and null otherwise
        LeftAlone offer(Object associatedObject, Association association, String nestedPath) {
            if (cascadeListener == null || cascadeListener.test(associatedObject)) {
                return null
            }
            LeftAlone object = new LeftAlone(associatedObject, association, nestedPath)
            leftAlone.add(object)
            object
        }
    }

    @CompileStatic
    private static class LeftAlone {

        final Object object
        final Association association
        final String nestedPath
        // The errors that the constraints of the object have recorded, by identity
        final Set<ObjectError> errors = Collections.newSetFromMap(new IdentityHashMap<ObjectError, Boolean>())

        LeftAlone(Object object, Association association, String nestedPath) {
            this.object = object
            this.association = association
            this.nestedPath = nestedPath
        }

        void addErrorsSince(Errors recorded, int errorCount) {
            List<ObjectError> all = recorded.getAllErrors()
            if (all.size() > errorCount) {
                errors.addAll(all.subList(errorCount, all.size()))
            }
        }
    }
}

package org.grails.scaffolding.model.property

import grails.gorm.validation.PersistentEntityValidator
import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.springframework.validation.Validator
import static grails.gorm.validation.ConstrainedProperty.BLANK_CONSTRAINT

/**
 * @see {@link DomainProperty}
 * @author James Kleeh
 */
@CompileStatic
class DomainPropertyImpl implements DomainProperty {

    @Delegate PersistentProperty<Property> persistentProperty
    PersistentProperty rootProperty
    PersistentEntity domainClass
    Constrained constrained
    String pathFromRoot

    protected Boolean convertEmptyStringsToNull
    protected Boolean trimStrings

    DomainPropertyImpl(PersistentProperty persistentProperty, MappingContext mappingContext) {
        this.persistentProperty = persistentProperty
        this.domainClass = persistentProperty.owner
        Validator validator = mappingContext.getEntityValidator(domainClass)
        if (validator instanceof PersistentEntityValidator) {
            this.constrained = new Constrained(((PersistentEntityValidator)validator).constrainedProperties.get(name))
        }
        if (this.constrained?.isNull()) {
            this.constrained = null
        }

        this.pathFromRoot = persistentProperty.name
    }

    DomainPropertyImpl(PersistentProperty rootProperty, PersistentProperty persistentProperty, MappingContext mappingContext) {
        this(persistentProperty, mappingContext)
        this.setRootProperty(rootProperty)
    }

    void setRootProperty(PersistentProperty rootProperty) {
        this.rootProperty = rootProperty
        this.pathFromRoot = "${rootProperty.name}.${name}"
    }

    Class getRootBeanType() {
        (rootProperty ?: persistentProperty).owner.javaClass
    }

    Class getBeanType() {
        owner.javaClass
    }

    Class getAssociatedType() {
        if (persistentProperty instanceof Association) {
            if (persistentProperty instanceof Basic) {
                ((Basic)persistentProperty).componentType
            } else {
                associatedEntity.javaClass
            }
        } else {
            null
        }
    }

    PersistentEntity getAssociatedEntity() {
        ((Association)persistentProperty).associatedEntity
    }

    boolean isRequired() {
        if (type in [Boolean, boolean]) {
            false
        } else if (type == String) {
            // if the property prohibits nulls and blanks are converted to nulls, then blanks will be prohibited even if a blank
            // constraint does not exist
            boolean hasBlankConstraint = constrained?.hasAppliedConstraint(BLANK_CONSTRAINT)
            boolean blanksImplicityProhibited = !hasBlankConstraint && !constrained?.nullable && convertEmptyStringsToNull && trimStrings
            !constrained?.nullable && (!constrained?.blank || blanksImplicityProhibited)
        } else {
            !constrained?.nullable
        }
    }

    List<String> getLabelKeys() {
        List<String> labelKeys = []
        labelKeys.add("${GrailsNameUtils.getPropertyName(beanType.simpleName)}.${name}.label".toString())
        if (rootProperty) {
            labelKeys.add("${GrailsNameUtils.getPropertyName(rootBeanType.simpleName)}.${pathFromRoot}.label".replaceAll(/\[(.+)\]/, '').toString())
        }
        labelKeys.unique()
    }

    String getDefaultLabel() {
        GrailsNameUtils.getNaturalName(name)
    }

    int compareTo(DomainProperty o2) {

        if (domainClass.mapping.identifier?.identifierName?.contains(name)) {
            return -1
        }
        if (domainClass.mapping.identifier?.identifierName?.contains(o2.name)) {
            return 1
        }

        Constrained cp2 = o2.constrained

        if (constrained == null  && cp2 == null) {
            return name.compareTo(o2.name)
        }

        if (constrained == null) {
            return 1
        }

        if (cp2 == null) {
            return -1
        }

        if (constrained.order > cp2.order) {
            return 1
        }

        if (constrained.order < cp2.order) {
            return -1
        }

        return 0
    }
}

package org.grails.scaffolding.model.property

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty

/**
 * An API to join the {@link PersistentProperty} to the {@link org.springframework.validation.Validator}
 * to assist with scaffolding
 *
 * @author James Kleeh
 */
interface DomainProperty extends PersistentProperty, Comparable<DomainProperty> {

    /**
     * @return The path of the property from the root domain class
     */
    String getPathFromRoot()

    /**
     * @return The {@link PersistentProperty} that represents this property
     */
    PersistentProperty getPersistentProperty()

    /**
     * @return The {@link PersistentEntity} the property belongs to
     */
    PersistentEntity getDomainClass()

    /**
     * @return The constraints of the property
     */
    Constrained getConstrained()

    /**
     * @return The root property
     */
    PersistentProperty getRootProperty()

    /**
     * Sets the root property
     *
     * @param rootProperty The root property
     */
    void setRootProperty(PersistentProperty rootProperty)

    /**
     * @return The class the root property belongs to
     */
    Class getRootBeanType()

    /**
     * @return The class the property belongs to
     */
    Class getBeanType()

    /**
     * @return The type of the association
     */
    Class getAssociatedType()

    /**
     * @return The associated entity if the property is an assocation
     */
    PersistentEntity getAssociatedEntity()

    /**
     * @return Whether or not the property is required
     */
    boolean isRequired()

    /**
     * @return i18n message keys to resolve the label of the property
     */
    List<String> getLabelKeys()

    /**
     * @return The default label for the property (natural name)
     */
    String getDefaultLabel()

}
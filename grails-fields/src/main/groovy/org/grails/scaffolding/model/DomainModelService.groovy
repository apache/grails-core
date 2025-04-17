package org.grails.scaffolding.model

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * An API to retrieve properties from a {@link PersistentEntity}
 *
 * @author James Kleeh
 */
interface DomainModelService {

    /**
     * The list of {@link DomainProperty} instances that allow for user input
     *
     * @param domainClass The persistent entity
     */
    List<DomainProperty> getInputProperties(PersistentEntity domainClass)
    List<DomainProperty> getInputProperties(PersistentEntity domainClass, List blackList)

    /**
     * The list of {@link DomainProperty} instances that are to be visible
     *
     * @param domainClass The persistent entity
     */
    List<DomainProperty> getOutputProperties(PersistentEntity domainClass)

    /**
     * The list of {@link DomainProperty} instances that are to be visible in a list context
     *
     * @param domainClass The persistent entity
     */
    List<DomainProperty> getListOutputProperties(PersistentEntity domainClass)

    /**
     * The list of {@link DomainProperty} instances that allow for user input and the closure returns true for
     *
     * @param domainClass The persistent entity
     * @param closure The closure that will be executed for each editable property
     */
    List<DomainProperty> findInputProperties(PersistentEntity domainClass, Closure closure)

    /**
     * Determines if the closure returns true for any input {@link DomainProperty}
     *
     * @param domainClass The persistent entity
     * @param closure The closure that will be executed for each property
     */
    Boolean hasInputProperty(PersistentEntity domainClass, Closure closure)

}

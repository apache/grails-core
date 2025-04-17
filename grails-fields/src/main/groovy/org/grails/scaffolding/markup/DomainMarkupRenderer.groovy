package org.grails.scaffolding.markup

import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Used to output markup that represents a given domain class.
 *
 * @author James Kleeh
 */
interface DomainMarkupRenderer {

    /**
     * Designed to render a "show" page that will display a single domain class instance.
     *
     * @param domainClass The domain class to be rendered
     * @return The rendered html
     */
    String renderOutput(PersistentEntity domainClass)

    /**
     * Designed to render a "list" page that will display a list of domain class instances.
     *
     * @param domainClass The domain class to be rendered
     * @return The rendered html
     */
    String renderListOutput(PersistentEntity domainClass)


    /**
     * Designed to render a form that will allow users to create or edit domain class instances.
     *
     * @param domainClass The domain class to be rendered
     * @return The rendered html
     */
    String renderInput(PersistentEntity domainClass)

}
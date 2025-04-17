package org.grails.scaffolding.registry

import org.grails.scaffolding.model.property.DomainProperty

/**
 * Used to render markup for a domain class property
 *
 * @author James Kleeh
 */
interface DomainRenderer {

    /**
     * Determines if the renderer supports rendering the given property
     *
     * @param property The domain property to be rendered
     * @return Whether or not the property is supported
     */
    boolean supports(DomainProperty property)

}
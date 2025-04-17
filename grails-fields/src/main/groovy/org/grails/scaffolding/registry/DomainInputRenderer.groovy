package org.grails.scaffolding.registry

import org.grails.scaffolding.model.property.DomainProperty

/**
 * Used to render a single domain class property on a form
 *
 * @author James Kleeh
 */
interface DomainInputRenderer extends DomainRenderer {

    /**
     * Defines how a given domain class property will be rendered in the context of a form
     *
     * @param defaultAttributes The default html element attributes
     * @param property The domain property to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    Closure renderInput(Map defaultAttributes, DomainProperty property)

}

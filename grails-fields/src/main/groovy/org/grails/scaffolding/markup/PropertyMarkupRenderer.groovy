package org.grails.scaffolding.markup

import org.grails.scaffolding.model.property.DomainProperty

/**
 * Used to render markup that represents a single domain class property
 *
 * @author James Kleeh
 */
trait PropertyMarkupRenderer {

    /**
     * Builds the standard html attributes that will be passed to {@link grails.plugin.scaffolding.registry.DomainInputRenderer#renderInput}
     *
     * @param property The domain property to be rendered
     * @return A map of the standard attributes
     */
    Map getStandardAttributes(DomainProperty property) {
        final String name = property.pathFromRoot
        Map attributes = [:]
        if (property.required) {
            attributes.required = null
        }
        if (property.constrained && !property.constrained.editable) {
            attributes.readonly = null
        }
        attributes.name = name
        attributes.id = name
        attributes
    }

    /**
     * Defines how a given domain class property will be rendered in the context of a list of domains class instances
     *
     * @param property The domain property to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    abstract Closure renderListOutput(DomainProperty property)

    /**
     * Defines how a given domain class property will be rendered in the context of a single domains class instance
     *
     * @param property The domain property to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    abstract Closure renderOutput(DomainProperty property)

    /**
     * Defines how a given domain class property will be rendered in the context of a form
     *
     * @param property The domain property to be rendered
     * @return The closure to be passed to an instance of {@link groovy.xml.MarkupBuilder}
     */
    abstract Closure renderInput(DomainProperty property)
}
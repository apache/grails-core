package org.grails.scaffolding.registry.input

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer

/**
 * The default renderer for rendering boolean properties
 *
 * @author James Kleeh
 */
class BooleanInputRenderer implements DomainInputRenderer {

    @Override
    boolean supports(DomainProperty domainProperty) {
        domainProperty.type in [boolean, Boolean]
    }

    @Override
    Closure renderInput(Map standardAttributes, DomainProperty domainProperty) {
        standardAttributes.type = "checkbox"
        return { ->
            input(standardAttributes)
        }
    }
}

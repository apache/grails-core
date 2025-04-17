package org.grails.scaffolding.registry.input

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer

/**
 * The renderer chosen for outputting domain properties when no other
 * renderers support the given property
 *
 * @author James Kleeh
 */
class DefaultInputRenderer implements DomainInputRenderer {

    @Override
    boolean supports(DomainProperty property) {
        true
    }

    @Override
    Closure renderInput(Map attributes, DomainProperty property) {
        { -> }
    }

}

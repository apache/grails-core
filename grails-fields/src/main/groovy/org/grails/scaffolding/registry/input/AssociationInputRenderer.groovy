package org.grails.scaffolding.registry.input

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer
import org.grails.datastore.mapping.model.types.Association

/**
 * The default renderer for rendering associations
 *
 * @author James Kleeh
 */
class AssociationInputRenderer implements DomainInputRenderer {

    @Override
    boolean supports(DomainProperty property) {
        property.persistentProperty instanceof Association
    }

    @Override
    Closure renderInput(Map defaultAttributes, DomainProperty property) {
        { -> }
    }
}

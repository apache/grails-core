package org.grails.scaffolding.registry.input

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer
import grails.util.GrailsNameUtils
import grails.web.mapping.LinkGenerator
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.ToMany

/**
 * The default renderer for rendering bidirectional to many associations
 *
 * @author James Kleeh
 */
class BidirectionalToManyInputRenderer implements DomainInputRenderer {

    protected LinkGenerator linkGenerator

    BidirectionalToManyInputRenderer(LinkGenerator linkGenerator) {
        this.linkGenerator = linkGenerator
    }

    @Override
    boolean supports(DomainProperty property) {
        PersistentProperty persistentProperty = property.persistentProperty
        persistentProperty instanceof ToMany && persistentProperty.bidirectional
    }

    protected String getPropertyName(DomainProperty property) {
        GrailsNameUtils.getPropertyName(property.rootBeanType)
    }

    protected String getAssociatedClassName(DomainProperty property) {
        property.associatedType.simpleName
    }

    @Override
    Closure renderInput(Map defaultAttributes, DomainProperty property) {
        final String objectName = "${getPropertyName(property)}.id"
        defaultAttributes.remove('required')
        defaultAttributes.remove('readonly')
        defaultAttributes.href = linkGenerator.link(resource: property.associatedType, action: "create", params: [(objectName): ""])
        return { ->
            a("Add ${getAssociatedClassName(property)}", defaultAttributes)
        }
    }
}

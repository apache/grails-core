package org.grails.scaffolding.registry.input

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.scaffolding.model.property.Constrained
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer

/**
 * The default renderer for rendering {@link String} properties
 *
 * @author James Kleeh
 */
@CompileStatic
class StringInputRenderer implements DomainInputRenderer {

    @Override
    boolean supports(DomainProperty domainProperty) {
        domainProperty.type in [String, null]
    }

    @Override
    @CompileStatic(TypeCheckingMode.SKIP)
    Closure renderInput(Map standardAttributes, DomainProperty domainProperty) {
        Constrained constraints = domainProperty.constrained
        if (constraints?.password) {
            standardAttributes.type = "password"
        } else if (constraints?.email)  {
            standardAttributes.type = "email"
        } else if (constraints?.url) {
            standardAttributes.type = "url"
        } else {
            standardAttributes.type = "text"
        }

        if (constraints?.matches) {
            standardAttributes.pattern = constraints.matches
        }
        if (constraints?.maxSize) {
            standardAttributes.maxlength = constraints.maxSize
        }

        return { ->
            input(standardAttributes)
        }
    }
}

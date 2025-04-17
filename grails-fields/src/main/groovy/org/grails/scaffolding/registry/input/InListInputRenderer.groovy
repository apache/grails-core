package org.grails.scaffolding.registry.input

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer

/**
 * The default renderer for rendering properties with an inList constraint
 *
 * @author James Kleeh
 */
@CompileStatic
class InListInputRenderer implements DomainInputRenderer {

    @Override
    boolean supports(DomainProperty domainProperty) {
        domainProperty.constrained?.inList
    }

    @Override
    @CompileStatic(TypeCheckingMode.SKIP)
    Closure renderInput(Map standardAttributes, DomainProperty domainProperty) {
        List inList = domainProperty.constrained?.inList

        return { ->
            select(standardAttributes) {
                inList.each {
                    option(it, [value: it])
                }
            }
        }
    }
}

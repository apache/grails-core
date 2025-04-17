package org.grails.scaffolding.registry.input

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer

/**
 * The default renderer for rendering properties with the constraint {@code [widget: "textarea"]}
 *
 * @author James Kleeh
 */
@CompileStatic
class TextareaInputRenderer implements DomainInputRenderer {

    @Override
    boolean supports(DomainProperty domainProperty) {
        domainProperty.constrained?.widget == "textarea"
    }

    @Override
    @CompileStatic(TypeCheckingMode.SKIP)
    Closure renderInput(Map defaultAttributes, DomainProperty domainProperty) {
        Integer maxSize = domainProperty.constrained?.maxSize
        if (maxSize) {
            defaultAttributes.maxlength = maxSize
        }
        return { ->
            textarea(defaultAttributes)
        }
    }
}

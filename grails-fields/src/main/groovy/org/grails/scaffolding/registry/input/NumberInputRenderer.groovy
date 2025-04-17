package org.grails.scaffolding.registry.input

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.scaffolding.model.property.Constrained
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer

/**
 * The default renderer for rendering {@link Number} or primitive properties
 *
 * @author James Kleeh
 */
@CompileStatic
class NumberInputRenderer implements DomainInputRenderer {

    @Override
    boolean supports(DomainProperty domainProperty) {
        Class type = domainProperty.type
        type.isPrimitive() || type in Number
    }

    @Override
    @CompileStatic(TypeCheckingMode.SKIP)
    Closure renderInput(Map attributes, DomainProperty property) {
        Constrained constraints = property.constrained
        Range range = constraints?.range
        if (range) {
            attributes.type = "range"
            attributes.min = range.from
            attributes.max = range.to
        } else {
            String typeName = property.type.simpleName.toLowerCase()

            attributes.type = "number"

            if(typeName in ['double', 'float', 'bigdecimal']) {
                attributes.step = "any"
            }
            if (constraints?.scale != null) {
                attributes.step = "0.${'0' * (constraints.scale - 1)}1"
            }
            if (constraints?.min != null) {
                attributes.min = constraints.min
            }
            if (constraints?.max != null) {
                attributes.max = constraints.max
            }
        }

        return { ->
            input(attributes)
        }
    }
}

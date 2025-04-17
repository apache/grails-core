package org.grails.scaffolding.registry.output

import grails.util.GrailsNameUtils
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainOutputRenderer

/**
 * The renderer chosen for displaying domain properties when no other
 * renderers support the given property
 *
 * @author James Kleeh
 */
class DefaultOutputRenderer implements DomainOutputRenderer {

    protected String buildPropertyPath(DomainProperty property) {
        StringBuilder sb = new StringBuilder()
        sb.append(GrailsNameUtils.getPropertyName(property.rootBeanType)).append('.')
        sb.append(property.pathFromRoot)
        sb.toString()
    }

    @Override
    boolean supports(DomainProperty property) {
        true
    }

    @Override
    Closure renderListOutput(DomainProperty property) {
        renderOutput(property)
    }

    @Override
    Closure renderOutput(DomainProperty property) {
        { ->
            span("\${${buildPropertyPath(property)}}")
        }
    }
}

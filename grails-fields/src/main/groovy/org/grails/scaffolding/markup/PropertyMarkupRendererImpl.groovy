package org.grails.scaffolding.markup

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainOutputRendererRegistry
import org.grails.scaffolding.registry.DomainInputRendererRegistry
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired

/**
 * @see {@link PropertyMarkupRenderer}
 * @author James Kleeh
 */
@CompileStatic
class PropertyMarkupRendererImpl implements PropertyMarkupRenderer {

    @Autowired
    DomainInputRendererRegistry domainInputRendererRegistry

    @Autowired
    DomainOutputRendererRegistry domainOutputRendererRegistry

    @Override
    Closure renderListOutput(DomainProperty property) {
        domainOutputRendererRegistry.get(property).renderListOutput(property)
    }

    @Override
    Closure renderOutput(DomainProperty property) {
        domainOutputRendererRegistry.get(property).renderOutput(property)
    }

    @Override
    Closure renderInput(DomainProperty property) {
        domainInputRendererRegistry.get(property).renderInput(getStandardAttributes(property), property)
    }
}

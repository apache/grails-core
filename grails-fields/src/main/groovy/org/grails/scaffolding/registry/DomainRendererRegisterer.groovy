package org.grails.scaffolding.registry

import org.grails.scaffolding.registry.input.*
import org.grails.scaffolding.registry.output.DefaultOutputRenderer
import grails.web.mapping.LinkGenerator
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import jakarta.annotation.PostConstruct

/**
 * Bean for registering the default domain renderers
 *
 * @author James Kleeh
 */
@CompileStatic
class DomainRendererRegisterer {

    @Autowired
    DomainInputRendererRegistry domainInputRendererRegistry

    @Autowired
    DomainOutputRendererRegistry domainOutputRendererRegistry

    @Autowired
    LinkGenerator grailsLinkGenerator

    @PostConstruct
    void registerRenderers() {
        domainInputRendererRegistry.registerDomainRenderer(new DefaultInputRenderer(), -3)
        domainInputRendererRegistry.registerDomainRenderer(new UrlInputRenderer(), -1)
        domainInputRendererRegistry.registerDomainRenderer(new TimeZoneInputRenderer(), -1)
        domainInputRendererRegistry.registerDomainRenderer(new TimeInputRenderer(), -1)
        domainInputRendererRegistry.registerDomainRenderer(new StringInputRenderer(), -2)
        domainInputRendererRegistry.registerDomainRenderer(new TextareaInputRenderer(), -2)
        domainInputRendererRegistry.registerDomainRenderer(new NumberInputRenderer(), -2)
        domainInputRendererRegistry.registerDomainRenderer(new LocaleInputRenderer(), -1)
        domainInputRendererRegistry.registerDomainRenderer(new InListInputRenderer(), -1)
        domainInputRendererRegistry.registerDomainRenderer(new FileInputRenderer(), -1)
        domainInputRendererRegistry.registerDomainRenderer(new EnumInputRenderer(), -1)
        domainInputRendererRegistry.registerDomainRenderer(new DateInputRenderer(), -1)
        domainInputRendererRegistry.registerDomainRenderer(new CurrencyInputRenderer(), -1)
        domainInputRendererRegistry.registerDomainRenderer(new BooleanInputRenderer(), -1)
        domainInputRendererRegistry.registerDomainRenderer(new BidirectionalToManyInputRenderer(grailsLinkGenerator), -1)
        domainInputRendererRegistry.registerDomainRenderer(new AssociationInputRenderer(), -2)

        domainOutputRendererRegistry.registerDomainRenderer(new DefaultOutputRenderer(), -1)
    }
}

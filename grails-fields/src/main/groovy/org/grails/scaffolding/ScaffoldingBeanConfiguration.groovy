package org.grails.scaffolding

import org.grails.scaffolding.markup.*
import org.grails.scaffolding.model.DomainModelService
import org.grails.scaffolding.model.DomainModelServiceImpl
import org.grails.scaffolding.model.property.DomainPropertyFactory
import org.grails.scaffolding.model.property.DomainPropertyFactoryImpl
import org.grails.scaffolding.registry.DomainInputRendererRegistry
import org.grails.scaffolding.registry.DomainOutputRendererRegistry
import org.grails.scaffolding.registry.DomainRendererRegisterer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ScaffoldingBeanConfiguration {

    @Bean
    ContextMarkupRenderer contextMarkupRenderer() {
        new ContextMarkupRendererImpl()
    }

    @Bean
    DomainMarkupRenderer domainMarkupRenderer() {
        new DomainMarkupRendererImpl()
    }

    @Bean
    PropertyMarkupRenderer propertyMarkupRenderer() {
        new PropertyMarkupRendererImpl()
    }

    @Bean
    DomainPropertyFactory domainPropertyFactory() {
        new DomainPropertyFactoryImpl()
    }

    @Bean
    DomainModelService domainModelService() {
        new DomainModelServiceImpl()
    }

    @Bean
    DomainInputRendererRegistry domainInputRendererRegistry() {
        new DomainInputRendererRegistry()
    }

    @Bean
    DomainOutputRendererRegistry domainOutputRendererRegistry() {
        new DomainOutputRendererRegistry()
    }

    @Bean
    DomainRendererRegisterer domainRendererRegisterer() {
        new DomainRendererRegisterer()
    }

}

/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

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

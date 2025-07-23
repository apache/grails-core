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

package org.grails.scaffolding.registry

import grails.web.mapping.LinkGenerator
import groovy.transform.CompileStatic
import jakarta.annotation.PostConstruct
import org.grails.scaffolding.registry.input.AssociationInputRenderer
import org.grails.scaffolding.registry.input.BidirectionalToManyInputRenderer
import org.grails.scaffolding.registry.input.BooleanInputRenderer
import org.grails.scaffolding.registry.input.CurrencyInputRenderer
import org.grails.scaffolding.registry.input.DateInputRenderer
import org.grails.scaffolding.registry.input.DefaultInputRenderer
import org.grails.scaffolding.registry.input.EnumInputRenderer
import org.grails.scaffolding.registry.input.FileInputRenderer
import org.grails.scaffolding.registry.input.InListInputRenderer
import org.grails.scaffolding.registry.input.LocaleInputRenderer
import org.grails.scaffolding.registry.input.NumberInputRenderer
import org.grails.scaffolding.registry.input.StringInputRenderer
import org.grails.scaffolding.registry.input.TextareaInputRenderer
import org.grails.scaffolding.registry.input.TimeInputRenderer
import org.grails.scaffolding.registry.input.TimeZoneInputRenderer
import org.grails.scaffolding.registry.input.UrlInputRenderer
import org.grails.scaffolding.registry.output.DefaultOutputRenderer
import org.springframework.beans.factory.annotation.Autowired

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

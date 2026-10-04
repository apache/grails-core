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
package org.grails.plugins.web.mapping.factory

import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.support.StaticApplicationContext
import spock.lang.Specification

class UrlMappingFactoryBeanSpec extends Specification {

    void 'the factory merges the urlMappings bean into the configured mappings'() {
        given:
        StaticApplicationContext ctx = new StaticApplicationContext()
        ctx.beanFactory.registerSingleton('urlMappings', [fromContext: 'ctx', shared: 'ctx'])
        UrlMappingFactoryBean factory = new UrlMappingFactoryBean()
        factory.applicationContext = ctx
        factory.mappings = [explicit: 'bean', shared: 'bean']
        factory.afterPropertiesSet()

        when:
        Map mappings = factory.getObject()

        then:
        factory.objectType == Map
        factory.singleton
        mappings == [explicit: 'bean', shared: 'ctx', fromContext: 'ctx']
        factory.getObject().is(mappings)
    }

    void 'a urlMappings bean that is not a map is ignored'() {
        given:
        StaticApplicationContext ctx = new StaticApplicationContext()
        ctx.registerBeanDefinition('urlMappings', new RootBeanDefinition(String, null, null))
        UrlMappingFactoryBean factory = new UrlMappingFactoryBean()
        factory.applicationContext = ctx
        factory.afterPropertiesSet()

        expect:
        factory.getObject() == [:]
    }

    void 'without a urlMappings bean the explicit mappings are returned'() {
        given:
        UrlMappingFactoryBean factory = new UrlMappingFactoryBean()
        factory.applicationContext = new StaticApplicationContext()
        factory.mappings = [a: 1]
        factory.afterPropertiesSet()

        expect:
        factory.getObject() == [a: 1]
    }

}

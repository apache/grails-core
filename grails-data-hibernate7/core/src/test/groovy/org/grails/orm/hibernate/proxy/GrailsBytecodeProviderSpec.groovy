/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.proxy

import org.hibernate.bytecode.enhance.spi.EnhancementContext
import spock.lang.Specification
import spock.lang.Subject

class GrailsBytecodeProviderSpec extends Specification {

    @Subject
    GrailsBytecodeProvider provider = new GrailsBytecodeProvider()

    def 'provider can be instantiated and returns non-null proxyHelper'() {
        expect:
        provider != null
        provider.proxyHelper != null
    }

    def 'getProxyFactoryFactory returns GrailsProxyFactoryFactory'() {
        expect:
        provider.getProxyFactoryFactory() instanceof GrailsProxyFactoryFactory
    }

    def 'getReflectionOptimizer with getter/setter names returns null'() {
        expect:
        provider.getReflectionOptimizer(String, ['getName'] as String[], ['setName'] as String[], [String] as Class[]) == null
    }

    def 'getReflectionOptimizer with propertyAccessMap returns null'() {
        expect:
        provider.getReflectionOptimizer(String, [:]) == null
    }

    def 'getEnhancer returns null'() {
        given:
        def context = Mock(EnhancementContext)

        expect:
        provider.getEnhancer(context) == null
    }

    def 'getProxyFactoryFactory can build proxy factory and basic proxy factory'() {
        given:
        def factory = provider.getProxyFactoryFactory() as GrailsProxyFactoryFactory

        when:
        def basicProxy = factory.buildBasicProxyFactory(String)

        then:
        basicProxy == null
    }
}

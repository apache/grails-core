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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import spock.lang.Specification

class GeneratedDomainClassLoaderSpec extends Specification {

    def "a class of the parent is loaded from the parent"() {
        given:
        GeneratedDomainClassLoader loader = new GeneratedDomainClassLoader(getClass().classLoader)

        expect:
        loader.loadClass(String.name) == String
        loader.loadClass(GeneratedDomainClassLoaderSpec.name) == GeneratedDomainClassLoaderSpec
        loader.parent == getClass().classLoader
    }

    def "a class of a registered loader is loaded by name"() {
        given:
        ClassLoader generated = new GroovyClassLoader(getClass().classLoader)
        Class<?> made = generated.parseClass('package generatedloader\nclass Made { }')
        GeneratedDomainClassLoader loader = new GeneratedDomainClassLoader(getClass().classLoader)

        when:
        loader.loadClass('generatedloader.Made')

        then: 'not visible before the loader is registered'
        thrown(ClassNotFoundException)

        when:
        loader.register(generated)

        then:
        loader.loadClass('generatedloader.Made').is(made)
    }

    def "a class that no loader knows is not found"() {
        given:
        GeneratedDomainClassLoader loader = new GeneratedDomainClassLoader(getClass().classLoader)
        loader.register(new GroovyClassLoader(getClass().classLoader))

        when:
        loader.loadClass('generatedloader.Missing')

        then:
        ClassNotFoundException e = thrown()
        e.message.contains('generatedloader.Missing')
    }
}

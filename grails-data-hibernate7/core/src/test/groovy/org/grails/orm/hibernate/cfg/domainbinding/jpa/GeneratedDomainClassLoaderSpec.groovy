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

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import spock.lang.Specification

class GeneratedDomainClassLoaderSpec extends Specification {

    private ClassLoader generatedLoader(String name) {
        return new ByteBuddy().subclass(Object).name(name).make()
                .load(getClass().classLoader, ClassLoadingStrategy.Default.CHILD_FIRST).loaded.classLoader
    }

    def "a class that no generated loader defines is loaded from the parent"() {
        given:
        GeneratedDomainClassLoader loader = new GeneratedDomainClassLoader(getClass().classLoader)
        loader.register(generatedLoader('generatedloader.Other'))

        expect:
        loader.loadClass(String.name) == String
        loader.loadClass(GeneratedDomainClassLoaderSpec.name) == GeneratedDomainClassLoaderSpec
        loader.parent == getClass().classLoader
    }

    def "a class of a registered loader is loaded by name"() {
        given:
        ClassLoader generated = generatedLoader('generatedloader.Made')
        GeneratedDomainClassLoader loader = new GeneratedDomainClassLoader(getClass().classLoader)

        when:
        loader.loadClass('generatedloader.Made')

        then: 'not visible before the loader is registered'
        thrown(ClassNotFoundException)

        when:
        loader.register(generated)

        then:
        loader.loadClass('generatedloader.Made').classLoader.is(generated)
    }

    def "a generated class is preferred to a class of the same name of the parent"() {
        given: 'a generated class that has the name of a class the application loader knows'
        ClassLoader generated = generatedLoader(GeneratedDomainClassLoaderSpec.name)
        GeneratedDomainClassLoader loader = new GeneratedDomainClassLoader(getClass().classLoader)
        loader.register(generated)

        when:
        Class<?> resolved = loader.loadClass(GeneratedDomainClassLoaderSpec.name)

        then:
        resolved.name == GeneratedDomainClassLoaderSpec.name
        resolved.classLoader.is(generated)
        !resolved.is(GeneratedDomainClassLoaderSpec)
    }

    def "a class that no loader knows is not found"() {
        given:
        GeneratedDomainClassLoader loader = new GeneratedDomainClassLoader(getClass().classLoader)
        loader.register(generatedLoader('generatedloader.Other'))

        when:
        loader.loadClass('generatedloader.Missing')

        then:
        ClassNotFoundException e = thrown()
        e.message.contains('generatedloader.Missing')
    }
}

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
import org.hibernate.boot.registry.classloading.internal.ClassLoaderServiceImpl
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import spock.lang.Specification

class GeneratedDomainClassLoaderServiceSpec extends Specification {

    private static final String NAME = 'generatedloaderservice.Domain'

    def "a name resolves to the generated class until the service is switched, and to the real class from then on"() {
        given: 'a real class and a generated class of the same name, each in a loader of its own'
        ClassLoader realLoader = new ByteBuddy().subclass(Object).name(NAME).make()
                .load(getClass().classLoader, ClassLoadingStrategy.Default.WRAPPER).loaded.classLoader
        Class<?> real = realLoader.loadClass(NAME)
        GeneratedDomainClassLoader generatedLoader = new GeneratedDomainClassLoader(realLoader)
        Class<?> generated = new ByteBuddy().subclass(Object).name(NAME).make()
                .load(realLoader, ClassLoadingStrategy.Default.CHILD_FIRST).loaded
        generatedLoader.register(generated.classLoader)
        GeneratedDomainClassLoaderService service = new GeneratedDomainClassLoaderService(
                new ClassLoaderServiceImpl(generatedLoader), new ClassLoaderServiceImpl(realLoader))

        expect:
        !real.is(generated)
        !service.usingRealClasses
        service.classForName(NAME).is(generated)

        when:
        service.useRealClasses()

        then: 'the same name, asked of the same service, now resolves to the real class'
        service.usingRealClasses
        service.classForName(NAME).is(real)
        service.classForName(String.name) == String

        cleanup:
        service.stop()
    }

    def "the Java services are those of the binding service before and after the switch"() {
        given:
        ClassLoaderService binding = Mock()
        ClassLoaderService real = Mock()
        GeneratedDomainClassLoaderService service = new GeneratedDomainClassLoaderService(binding, real)

        when:
        service.loadJavaServices(Runnable)
        service.useRealClasses()
        service.loadJavaServices(Runnable)

        then:
        2 * binding.loadJavaServices(Runnable) >> []
        0 * real.loadJavaServices(_)
    }

    def "resources, packages and class loader work go to the service in use, and stopping stops both"() {
        given:
        ClassLoaderService binding = Mock()
        ClassLoaderService real = Mock()
        GeneratedDomainClassLoaderService service = new GeneratedDomainClassLoaderService(binding, real)
        ClassLoaderService.Work<String> work = { ClassLoader loader -> 'done' } as ClassLoaderService.Work<String>

        when:
        service.locateResource('a')
        service.locateResourceStream('b')
        service.locateResources('c')
        service.packageForNameOrNull('d')
        service.workWithClassLoader(work)

        then:
        1 * binding.locateResource('a')
        1 * binding.locateResourceStream('b')
        1 * binding.locateResources('c')
        1 * binding.packageForNameOrNull('d')
        1 * binding.workWithClassLoader(work)
        0 * real._

        when:
        service.useRealClasses()
        service.locateResource('a')
        service.locateResourceStream('b')
        service.locateResources('c')
        service.packageForNameOrNull('d')
        service.workWithClassLoader(work)
        service.generateProxy({ proxy, method, args -> null }, Runnable)
        service.stop()

        then:
        1 * real.locateResource('a')
        1 * real.locateResourceStream('b')
        1 * real.locateResources('c')
        1 * real.packageForNameOrNull('d')
        1 * real.workWithClassLoader(work)
        1 * real.generateProxy(_, Runnable)
        1 * real.stop()
        1 * binding.stop()
        0 * binding._
    }
}

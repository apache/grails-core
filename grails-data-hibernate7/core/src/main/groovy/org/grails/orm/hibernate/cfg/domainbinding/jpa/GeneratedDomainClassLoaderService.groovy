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

import java.lang.reflect.InvocationHandler

import groovy.transform.CompileStatic
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService

/**
 * Hibernate's class loader service for the registry of a generated-class binding: answers the domain class names with the
 * generated classes while the mappings are bound, and with the real classes from then on.
 *
 * <p>Hibernate resolves a class name through {@code Class.forName} on the loader of its class loader service, and the Java
 * virtual machine remembers the first answer a loader gave for a name: the same loader could never answer a name with another
 * class later. The switch is therefore made between two services, each with a loader of its own: the one the mappings are bound
 * with, and the one that resolves the same names to the application's classes. {@link #useRealClasses} switches, and every
 * lookup Hibernate makes afterwards, such as the mapped class of an entity, the instantiators and the proxy factory, finds the
 * real class.</p>
 *
 * @since 9.0
 */
@CompileStatic
class GeneratedDomainClassLoaderService implements ClassLoaderService {

    private final ClassLoaderService binding
    private final ClassLoaderService real
    private volatile ClassLoaderService current

    /**
     * @param binding the service whose loader resolves the domain class names to the generated classes
     * @param real the service whose loader resolves them to the application's classes
     */
    GeneratedDomainClassLoaderService(ClassLoaderService binding, ClassLoaderService real) {
        this.binding = binding
        this.real = real
        this.current = binding
    }

    /**
     * From now on a domain class name is resolved to the application's class.
     */
    void useRealClasses() {
        current = real
    }

    boolean isUsingRealClasses() {
        return current.is(real)
    }

    @Override
    <T> Class<T> classForName(String className) {
        return current.<T>classForName(className)
    }

    @Override
    URL locateResource(String name) {
        return current.locateResource(name)
    }

    @Override
    InputStream locateResourceStream(String name) {
        return current.locateResourceStream(name)
    }

    @Override
    List<URL> locateResources(String name) {
        return current.locateResources(name)
    }

    @Override
    <S> Collection<S> loadJavaServices(Class<S> serviceContract) {
        // Java services are found by the names of their implementations, which no generated class shadows: one set of
        // instances serves before and after the switch
        return binding.loadJavaServices(serviceContract)
    }

    @Override
    <T> T generateProxy(InvocationHandler handler, Class<?>... interfaces) {
        return current.<T>generateProxy(handler, interfaces)
    }

    @Override
    Package packageForNameOrNull(String packageName) {
        return current.packageForNameOrNull(packageName)
    }

    @Override
    <T> T workWithClassLoader(ClassLoaderService.Work<T> work) {
        return current.workWithClassLoader(work)
    }

    @Override
    void stop() {
        binding.stop()
        real.stop()
    }
}

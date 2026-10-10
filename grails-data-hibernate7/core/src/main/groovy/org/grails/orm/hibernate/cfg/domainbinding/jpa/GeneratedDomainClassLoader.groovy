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

import java.util.concurrent.CopyOnWriteArrayList

import groovy.transform.CompileStatic

/**
 * The class loader Hibernate's annotation binder resolves the generated domain classes through.
 *
 * <p>The classes {@link GrailsDomainGenerator#generateAll} makes carry the names of the domain classes they describe, and
 * live in a class loader of their own, whose parent is the application's. While Hibernate binds the mappings, a domain
 * class name must resolve to the generated class, so this loader answers with the classes of the loaders registered with
 * it first and only then with its parent's; the generated loaders delegate everything they did not generate to the
 * application loader, not back to this one, so no lookup can loop.</p>
 *
 * <p>This loader is only used while the mappings are bound. A class loader answers a name the same way for ever once it has
 * been asked through {@code Class.forName}, which is what Hibernate's class loader service does, so the switch to the real
 * classes is made by {@link GeneratedDomainClassLoaderService}, not by this loader.</p>
 *
 * @since 9.0
 */
@CompileStatic
class GeneratedDomainClassLoader extends ClassLoader {

    private final List<ClassLoader> generated = new CopyOnWriteArrayList<ClassLoader>()

    GeneratedDomainClassLoader(ClassLoader parent) {
        super(parent)
    }

    /**
     * Makes the classes of a loader that {@link GrailsDomainGenerator} created loadable by name through this loader, in
     * preference to the classes of the same name of the parent.
     */
    void register(ClassLoader generatedClassLoader) {
        generated.add(generatedClassLoader)
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        for (ClassLoader loader : generated) {
            Class<?> made = generatedClass(loader, name)
            if (made != null) {
                return made
            }
        }
        return super.loadClass(name, resolve)
    }

    /**
     * A generated loader delegates the names it did not generate to its parent, so only a class it defined itself counts.
     */
    private static Class<?> generatedClass(ClassLoader loader, String name) {
        try {
            Class<?> candidate = Class.forName(name, false, loader)
            return candidate.classLoader.is(loader) ? candidate : null
        }
        catch (ClassNotFoundException ignored) {
            return null
        }
    }
}

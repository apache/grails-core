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
 * <p>The classes {@link GrailsDomainGenerator#generateAll} makes live in a class loader of their own, whose parent is the
 * application's. Hibernate loads an annotated class by name, so its class loader service must be able to see them; this
 * loader sits between that service and the application: it answers with its parent's classes first and, failing that,
 * with the classes of the loaders registered with it. The generated loaders do not delegate back to this one (their
 * parent is the application loader), so no lookup can loop.</p>
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
     * Makes the classes of a loader that {@link GrailsDomainGenerator} created loadable by name through this loader.
     */
    void register(ClassLoader generatedClassLoader) {
        generated.add(generatedClassLoader)
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return super.loadClass(name, resolve)
        }
        catch (ClassNotFoundException notInParent) {
            for (ClassLoader loader : generated) {
                try {
                    return Class.forName(name, false, loader)
                }
                catch (ClassNotFoundException ignored) {
                    // not one of this loader's classes: try the next
                }
            }
            throw notInParent
        }
    }
}

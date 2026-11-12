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
package org.grails.boot.internal

import groovy.transform.CompileStatic

import org.grails.io.support.GrailsResourceUtils

/**
 * Finds the classes the plugins were told of that have changed shape since.
 *
 * <p>The development watch compiles a changed source file and tells the plugins straight away, with the class the
 * application has loaded. An agent that redefines a loaded class in place, such as Hotswap Agent or a debugger on the
 * JetBrains Runtime, replaces that class only after the class file is written, so the plugins are told of the class
 * as it was, and a controller action added to it is not registered. A class that gains or loses a method or a field
 * after its shape was recorded for the plugins has been redefined, and its source file is returned so that they can
 * be told again.</p>
 *
 * @since 8.1
 */
@CompileStatic
class RedefinedClassWatch {

    private final ClassLoader classLoader
    private final Map<File, Set<String>> shapes = new LinkedHashMap<>()

    RedefinedClassWatch(ClassLoader classLoader) {
        this.classLoader = classLoader
    }

    /**
     * Records the shape of the class compiled from a source file, then tells the plugins of it. The shape is recorded
     * first, so that a class an agent redefines while the plugins are told of it is found changed afterwards.
     *
     * @param source the source file to tell the plugins of
     * @param tellPlugins tells the plugins of the source file
     */
    void tell(File source, Runnable tellPlugins) {
        Set<String> shape = shapeOf(source)
        if (shape != null) {
            shapes.put(source, shape)
        }
        tellPlugins.run()
    }

    /**
     * The source files whose class has gained or lost a method or a field since its shape was recorded. Each is
     * recorded with the shape it has now, before the plugins are told of it again, so it is returned once for each
     * change.
     *
     * @return the source files to tell the plugins of again
     */
    List<File> redefined() {
        List<File> redefined = []
        for (Map.Entry<File, Set<String>> entry : shapes.entrySet()) {
            Set<String> shape = shapeOf(entry.key)
            if (shape != null && shape != entry.value) {
                entry.value = shape
                redefined.add(entry.key)
            }
        }
        redefined
    }

    private Set<String> shapeOf(File source) {
        String className = GrailsResourceUtils.getClassName(source.path)
        if (className == null) {
            return null
        }
        try {
            Class<?> loaded = classLoader.loadClass(className)
            Set<String> shape = new HashSet<>()
            loaded.declaredMethods.each { shape.add(it.toString()) }
            loaded.declaredFields.each { shape.add(it.toString()) }
            return shape
        }
        catch (ClassNotFoundException | LinkageError ignored) {
            return null
        }
    }
}

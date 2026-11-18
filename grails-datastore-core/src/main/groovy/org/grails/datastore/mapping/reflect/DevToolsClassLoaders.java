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
package org.grails.datastore.mapping.reflect;

/**
 * Resolves the class loader GORM and Hibernate should use when Spring Boot DevTools
 * restart is active.
 *
 * <p>DevTools splits the classpath across a base loader (third-party jars, including
 * GORM and Hibernate) and a {@code RestartClassLoader} (application classes). Hibernate's
 * JPA metamodel and GORM's entity registry key entities by {@link Class} identity, so a
 * domain class Hibernate resolved through the base loader is "not an entity" to code
 * holding the restart loader's copy. Hibernate resolves entities by name through the
 * loader it is bootstrapped with, so that loader must see the restarted classes.</p>
 *
 * <p>The thread context class loader is not always the {@code RestartClassLoader} itself
 * while the application starts. A servlet container swaps in its own web application
 * loader during context start (Tomcat's {@code TomcatEmbeddedWebappClassLoader}), and
 * that loader delegates to the {@code RestartClassLoader} as its parent. Every check here
 * therefore walks the parent chain rather than matching only the loader's own type.</p>
 *
 * @since 8.0
 */
public final class DevToolsClassLoaders {

    private static final String RESTART_CLASS_LOADER_NAME =
            "org.springframework.boot.devtools.restart.classloader.RestartClassLoader";
    private static final String RESTART_CLASS_LOADER_SIMPLE_NAME = "RestartClassLoader";

    private DevToolsClassLoaders() {
    }

    /**
     * @param classLoader the loader to inspect, possibly {@code null}
     * @return {@code true} when {@code classLoader} is Spring Boot DevTools'
     * {@code RestartClassLoader} itself
     */
    public static boolean isRestartClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        Class<?> type = classLoader.getClass();
        while (type != null && type != Object.class) {
            if (RESTART_CLASS_LOADER_NAME.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        // Fallback for tests and shaded/relocated DevTools copies.
        return RESTART_CLASS_LOADER_SIMPLE_NAME.equals(classLoader.getClass().getSimpleName());
    }

    /**
     * @param classLoader the loader to inspect, possibly {@code null}
     * @return {@code true} when {@code classLoader} is the {@code RestartClassLoader} or
     * delegates to one through its parent chain, and so sees the restarted application classes
     */
    public static boolean isRestartClassLoaderOrDescendant(ClassLoader classLoader) {
        for (ClassLoader current = classLoader; current != null; current = current.getParent()) {
            if (isRestartClassLoader(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a loader that sees the restarted application classes when DevTools restart is
     * active. {@code fallback} is kept when it already does; otherwise the thread context class
     * loader is used when it does. When neither does, DevTools restart is not active on this
     * thread and {@code fallback} is returned, or this class's loader when {@code fallback} is
     * {@code null}.
     *
     * @param fallback the loader to use when DevTools restart is not active
     * @return a non-null class loader
     */
    @SuppressWarnings("PMD.UseProperClassLoader") // last-resort fallback once neither candidate sees the restart loader
    public static ClassLoader preferRestartClassLoader(ClassLoader fallback) {
        if (isRestartClassLoaderOrDescendant(fallback)) {
            return fallback;
        }
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (isRestartClassLoaderOrDescendant(contextClassLoader)) {
            return contextClassLoader;
        }
        if (fallback != null) {
            return fallback;
        }
        return DevToolsClassLoaders.class.getClassLoader();
    }
}

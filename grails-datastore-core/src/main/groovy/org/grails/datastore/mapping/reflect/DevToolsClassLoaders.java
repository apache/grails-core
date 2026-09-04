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
 * is on the classpath.
 *
 * <p>DevTools splits the classpath across a base loader (third-party jars) and a
 * {@code RestartClassLoader} (application classes). Hibernate's JPA metamodel and
 * GORM's entity registry key entities by {@link Class} identity, so domain classes
 * loaded by the restart loader are "not an entity" if Hibernate resolved them
 * through the base loader. Preferring the restart loader — typically the thread
 * context class loader on {@code restartedMain} — keeps those identities aligned.</p>
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
     * {@code RestartClassLoader}
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
     * Prefer the thread context class loader when it is DevTools'
     * {@code RestartClassLoader}, unless {@code fallback} is that loader or a
     * descendant of it (the child can see everything the restart loader sees
     * plus its own classes). Otherwise return {@code fallback}, or this class's
     * loader when {@code fallback} is {@code null}.
     *
     * @param fallback the loader to use when DevTools is not active
     * @return a non-null class loader
     */
    @SuppressWarnings("PMD.UseProperClassLoader") // last-resort fallback; TCCL is checked first when it is a RestartClassLoader
    public static ClassLoader preferRestartClassLoader(ClassLoader fallback) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (isRestartClassLoader(contextClassLoader)) {
            if (isLoaderOrDescendant(fallback, contextClassLoader)) {
                return fallback;
            }
            return contextClassLoader;
        }
        if (fallback != null) {
            return fallback;
        }
        return DevToolsClassLoaders.class.getClassLoader();
    }

    /**
     * @deprecated use {@link #preferRestartClassLoader(ClassLoader)}
     */
    @Deprecated
    public static ClassLoader resolve(ClassLoader fallback) {
        return preferRestartClassLoader(fallback);
    }

    private static boolean isLoaderOrDescendant(ClassLoader candidate, ClassLoader ancestor) {
        ClassLoader current = candidate;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}

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
package org.apache.grails.common.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.ClassUtils;

/**
 * Reads the properties of a bean whose class is not {@code public}.
 *
 * <p>A class that is not public -- anonymous, local, or package-private -- cannot have its read
 * methods invoked from another package even when the methods themselves are public, so reflection
 * over such a bean needs help. Groovy 4 stamped {@code ACC_PUBLIC} on anonymous inner classes and
 * Groovy 5 does not, which is why beans of that shape reach the framework at all.
 *
 * <p>This compatibility handling is deliberately visible: {@link #warnOnNonPublicClass} reports the
 * class once so an application can be corrected, and so the handling can be withdrawn if the
 * compiler stops producing non-public classes for this shape.
 *
 * @since 8.0.0
 */
public final class ReflectionUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectionUtils.class);

    /** Best-effort upper bound, so that generated class names cannot grow the set without limit. */
    private static final int MAX_WARNED_CLASSES = 1024;

    /**
     * Keyed by class name rather than by {@link Class}, so that reporting a class never retains its
     * class loader. A reload therefore does not report the same class again, which is intended: a
     * reload loop would otherwise repeat every warning.
     */
    private static final Set<String> WARNED_NON_PUBLIC_CLASSES = ConcurrentHashMap.newKeySet();

    /**
     * Packages whose classes an application cannot declare public itself, so reporting them would
     * only be noise. {@code java.util.KeyValueHolder}, handed out by {@code Map.entry}, is the one
     * that turns up in practice.
     *
     * <p>The {@code grails} packages are deliberately absent: a framework class reaching this code
     * is worth seeing, and the tests declare their fixtures under {@code org.grails}.
     */
    private static final String[] NON_APPLICATION_PACKAGES = {
        "java.", "javax.", "jakarta.", "groovy.", "org.apache.groovy.", "org.codehaus.groovy.",
        "org.springframework."
    };

    private ReflectionUtils() {
    }

    /**
     * Resolves a read method that can actually be invoked on {@code target}.
     *
     * <p>Where an equivalent method is declared by a public type anywhere in the hierarchy -- an
     * interface or a superclass -- that one is returned: it is declared by an accessible type, and
     * virtual dispatch still reaches the override. Only where there is no such type does the method
     * need help, and then a private copy is widened, so that the accessibility flag cannot leak
     * into a {@code Method} instance shared through a descriptor cache.
     *
     * @param readMethod  the property's read method, typically from a {@code PropertyDescriptor}
     * @param targetClass the class being read, used to resolve the publicly accessible method
     * @param target      the instance being read, or {@code null} for a static read method
     * @return a method that may be invoked on {@code target}
     */
    public static Method resolveInvokableReadMethod(Method readMethod, Class<?> targetClass, @Nullable Object target)
            throws NoSuchMethodException {
        Method invokable = ClassUtils.getPubliclyAccessibleMethodIfPossible(readMethod, targetClass);
        if (canAccess(invokable, target)) {
            return invokable;
        }
        // getDeclaredMethod cannot fail here: invokable was resolved from this very class's methods.
        Method widened = invokable.getDeclaringClass()
                .getDeclaredMethod(invokable.getName(), invokable.getParameterTypes());
        widened.setAccessible(true);
        return widened;
    }

    /**
     * Makes an instance field readable, widening it only when it is not readable already.
     *
     * <p>The field is expected to have come from {@link Class#getDeclaredFields}, which hands out a
     * fresh copy on every call, so widening it is confined to the caller's own copy.
     *
     * @param field an instance field
     * @param target the instance being read
     * @return {@code false} when the field cannot be read, in which case the caller should skip it
     *         rather than fail the whole read
     */
    public static boolean tryMakeReadable(Field field, @Nullable Object target) {
        if (canAccess(field, target)) {
            return true;
        }
        try {
            field.setAccessible(true);
            return true;
        }
        catch (InaccessibleObjectException | SecurityException e) {
            // The declaring class is in a named module that does not open its package to us
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cannot read field [{}] of [{}]: {}",
                        field.getName(), field.getDeclaringClass().getName(), e.getMessage());
            }
            return false;
        }
    }

    /**
     * Reports {@code clazz} if it is not public, at most once per class.
     *
     * <p>Synthetic classes and classes from the JDK, Groovy and Spring are not reported: they are
     * not written by the application whose log this is, so nobody reading it can act on them.
     *
     * <p>The bookkeeping deliberately runs before the log level is consulted, so that "once" does
     * not depend on how logging happens to be configured.
     *
     * @return whether this call was the one that reported the class
     */
    public static boolean warnOnNonPublicClass(@Nullable Class<?> clazz) {
        if (clazz == null || Modifier.isPublic(clazz.getModifiers()) || !isApplicationClass(clazz)) {
            return false;
        }
        if (WARNED_NON_PUBLIC_CLASSES.size() >= MAX_WARNED_CLASSES ||
                !WARNED_NON_PUBLIC_CLASSES.add(clazz.getName())) {
            return false;
        }
        if (LOG.isWarnEnabled()) {
            LOG.warn("Class [{}] is not public. Grails reads its properties through compatibility handling that may " +
                            "be withdrawn in a future major release. Declare it as a named public class so that it " +
                            "reads as a standard JavaBean, or register an ObjectMarshaller for it if the class is " +
                            "not yours. To silence this, set the log level of [{}] above WARN. " +
                            "(warned once per class)",
                    clazz.getName(), LOG.getName());
        }
        return true;
    }

    /**
     * Forgets which classes have been reported. Intended for tests, which would otherwise depend on
     * whether another test in the same JVM reported the same class first.
     */
    public static void resetWarnedClasses() {
        WARNED_NON_PUBLIC_CLASSES.clear();
    }

    /**
     * {@code canAccess} rejects a non-null instance for a static member, which a caller iterating
     * property descriptors or declared fields should not have to know.
     */
    private static boolean canAccess(Method method, @Nullable Object target) {
        return method.canAccess(Modifier.isStatic(method.getModifiers()) ? null : target);
    }

    private static boolean canAccess(Field field, @Nullable Object target) {
        return field.canAccess(Modifier.isStatic(field.getModifiers()) ? null : target);
    }

    private static boolean isApplicationClass(Class<?> clazz) {
        if (clazz.isSynthetic()) {
            return false;
        }
        String name = clazz.getName();
        for (String prefix : NON_APPLICATION_PACKAGES) {
            if (name.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }
}

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
package org.grails.datastore.mapping.internal;

/**
 * <p><strong>Internal API. Do not use it from application or plugin code.</strong> It is public only because the
 * datastore modules that share it are separate artifacts, and Java cannot restrict a type to a set of artifacts. It is
 * not part of the supported Grails API: it carries no compatibility promise, and it can change or be removed in any
 * release, including a patch release, without a deprecation period. A class you write against it can stop compiling
 * on the next upgrade.</p>
 *
 * <p>An enum whose constants each stand for one thing that is told apart by the class of an object, such as the
 * database a driver or a dialect object is for. A constant lists the classes it is for; the one listing
 * {@link Object} is the default, chosen for an object no other constant lists; a constant listing none is never
 * chosen for an object. The constants are tried in their declared order, so a constant for a subclass is declared
 * before the constant for its superclass.</p>
 *
 * <p>This type knows nothing about any database. What a constant stands for, and the properties it carries, belong to
 * the module that declares the enum: a SQL module declares which databases it supports, and a module for a store that
 * is not SQL declares its own, and neither sees the other's.</p>
 *
 * @param <T> the enum
 * @since 9.0
 */
public interface ClassKeyed<T extends Enum<T> & ClassKeyed<T>> {

    /** The classes the constant is for: any of their instances is told apart as this constant. */
    Class<?>[] keyClasses();

    /**
     * Returns the constant of the enum for the object: the first whose classes the object is an instance of, or else
     * the first listing {@link Object} itself.
     *
     * @throws IllegalArgumentException if no constant is for the object and none is the default
     */
    static <T extends Enum<T> & ClassKeyed<T>> T forInstance(Class<T> type, Object instance) {
        T fallback = null;
        for (T candidate : type.getEnumConstants()) {
            for (Class<?> keyClass : candidate.keyClasses()) {
                if (keyClass == Object.class) {
                    if (fallback == null) {
                        fallback = candidate;
                    }
                } else if (keyClass.isInstance(instance)) {
                    return candidate;
                }
            }
        }
        if (fallback == null) {
            throw new IllegalArgumentException(type.getName() + " has no constant for " +
                    (instance == null ? "null" : instance.getClass().getName()));
        }
        return fallback;
    }
}

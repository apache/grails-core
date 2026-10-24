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

package org.grails.orm.hibernate.dialect;

import org.hibernate.dialect.Dialect;

/**
 * An enum whose constants each stand for the databases of some Hibernate dialects, so that one of them is chosen
 * for the dialect a session factory connects with. A constant lists the dialect classes it is for; the one listing
 * {@link Dialect} itself is the default, chosen for a dialect no other constant lists; a constant listing none is
 * never chosen for a dialect. The constants are tried in their declared order, so a constant for a dialect
 * subclass is declared before the constant for its superclass.
 *
 * @param <T> the enum
 * @since 8.0.1
 */
public interface DialectKeyed<T extends Enum<T> & DialectKeyed<T>> {

    /** The dialect classes the constant is for: the database of any of their instances. */
    Class<? extends Dialect>[] dialectClasses();

    /**
     * Returns the constant of the enum for the dialect: the first whose dialect classes the dialect is an instance
     * of, or else the first listing {@link Dialect} itself.
     *
     * @throws IllegalArgumentException if no constant is for the dialect and none is the default
     */
    static <T extends Enum<T> & DialectKeyed<T>> T forDialect(Class<T> type, Dialect dialect) {
        T fallback = null;
        for (T candidate : type.getEnumConstants()) {
            for (Class<? extends Dialect> dialectClass : candidate.dialectClasses()) {
                if (dialectClass == Dialect.class) {
                    if (fallback == null) {
                        fallback = candidate;
                    }
                } else if (dialectClass.isInstance(dialect)) {
                    return candidate;
                }
            }
        }
        if (fallback == null) {
            throw new IllegalArgumentException(type.getName() + " has no constant for " + dialect.getClass().getName());
        }
        return fallback;
    }
}

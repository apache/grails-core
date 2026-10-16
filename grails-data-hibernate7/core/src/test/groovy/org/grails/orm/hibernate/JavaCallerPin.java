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
package org.grails.orm.hibernate;

import grails.orm.HibernateCriteriaBuilder;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyProvider;
import org.grails.orm.hibernate.query.HibernateAlias;

/**
 * Compiled together with the Groovy sources, so the Java-facing API is pinned at compile time: a constant that
 * stops being a field, a constructor that disappears, or a checked exception that is no longer declared (which
 * makes the {@code catch} below unreachable) fails the build of this class.
 */
@SuppressWarnings("removal")
final class JavaCallerPin {

    static final String MAX_ARGUMENT = GrailsHibernateUtil.ARGUMENT_MAX;

    static final Object BOOLEAN_TYPE = HibernateCriteriaBuilder.BOOLEAN;

    private JavaCallerPin() {
    }

    static HibernateAlias twoArgumentAlias() {
        return new HibernateAlias("face", "f");
    }

    static boolean configure(NamingStrategyProvider provider) {
        try {
            provider.configureNamingStrategy("default", "no.such.Strategy");
            return true;
        }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            return false;
        }
    }

}

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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.slf4j.Logger;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;

@SuppressWarnings("PMD.LoggerIsNotStaticFinal")
public class LogCascadeMapping {

    private final Logger log;

    public LogCascadeMapping(Logger log) {
        this.log = log;
    }

    /**
     * Logs the cascade mapping strategy for a given association if debug logging is enabled.
     *
     * @param association The association property.
     * @param cascadeStrategy The calculated cascade string.
     */
    public void logCascadeMapping(Association<?> association, CascadeBehavior cascadeStrategy) {
        if (log.isDebugEnabled()) {
            String assType = getAssociationType(association);
            log.debug(
                    "Mapping cascade strategy for {} property {}.{} referencing type [{}] -> [CASCADE: {}]",
                    assType,
                    association.getOwner().getName(),
                    association.getName(),
                    association.getAssociatedEntity().getJavaClass().getName(),
                    cascadeStrategy);
        }
    }

    /**
     * Determines the string representation of an association's type using a modern switch expression
     * with pattern matching.
     *
     * @param association The association to inspect.
     * @return A string describing the association type (e.g., "one-to-many").
     */
    private String getAssociationType(Association<?> association) {
        // Use a standard if-else-if chain for compatibility with Java 17 and earlier.
        if (association instanceof HibernateManyToManyProperty) {
            return "many-to-many";
        } else if (association instanceof HibernateOneToManyProperty) {
            return "one-to-many";
        } else if (association instanceof HibernateOneToOneProperty) {
            return "one-to-one";
        } else if (association instanceof HibernateManyToOneProperty) {
            return "many-to-one";
        } else if (association.isEmbedded()) {
            return "embedded";
        }
        return "unknown";
    }
}

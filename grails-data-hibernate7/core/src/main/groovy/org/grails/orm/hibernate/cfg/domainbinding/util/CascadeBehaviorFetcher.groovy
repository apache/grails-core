/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.util

import groovy.transform.CompileStatic
import org.hibernate.MappingException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.ALL
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.NONE
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.SAVE_UPDATE

/**
 * The cascade behavior fetcher class.
 */
@CompileStatic
class CascadeBehaviorFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(CascadeBehaviorFetcher)

    private final LogCascadeMapping logCascadeMapping

    /**
     * Creates a new {@link CascadeBehaviorFetcher} instance.
     */
    CascadeBehaviorFetcher(LogCascadeMapping logCascadeMapping) {
        this.logCascadeMapping = logCascadeMapping
    }

    /**
     * Creates a new {@link CascadeBehaviorFetcher} instance.
     */
    CascadeBehaviorFetcher() {
        this(new LogCascadeMapping(LOG))
    }

    /**
     * Gets the cascade behaviour.
     */
    String getCascadeBehaviour(Association<?> association) {
        CascadeBehavior definedBehavior = getDefinedBehavior((HibernatePersistentProperty) association)
        CascadeBehavior impliedBehavior = getImpliedBehavior(association)
        CascadeBehavior cascadeStrategy = definedBehavior != null ? definedBehavior : impliedBehavior

        logCascadeMapping.logCascadeMapping(association, cascadeStrategy)

        return cascadeStrategy.value
    }

    private CascadeBehavior getDefinedBehavior(HibernatePersistentProperty grailsProperty) {
        PropertyConfig mappedForm = grailsProperty.mappedForm
        if (mappedForm == null) {
            return null
        }
        String cascade = mappedForm.cascade
        if (cascade == null) {
            return null
        }
        return CascadeBehavior.fromString(cascade)
    }

    private CascadeBehavior getImpliedBehavior(Association<?> association) {
        // Every to-many shape (Basic, Map-typed, EmbeddedCollection, OneToMany, ManyToMany) knows its
        // own implied cascade behavior; only the to-one/embedded/hasOne shapes remain here.
        if (association instanceof HibernateToManyProperty) {
            return ((HibernateToManyProperty) association).impliedCascadeBehavior
        }

        if (association instanceof Embedded) {
            return ALL
        }

        // Fail-fast only for entity relationships that are truly missing an association
        if (association.associatedEntity == null) {
            throw new MappingException("Relationship ${association} has no associated entity".toString())
        }

        if (association.isHasOne()) {
            return ALL
        }
        else if (association instanceof HibernateOneToOneProperty) {
            return association.isOwningSide() ? ALL : SAVE_UPDATE
        }
        else if (association instanceof HibernateManyToOneProperty) {
            if (association.isCorrectlyOwned() && !association.isCircular()) {
                return ALL
            }
            else if (association.isCompositeIdProperty()) {
                return ALL
            }
            else {
                return NONE
            }
        }
        else {
            throw new MappingException("Unrecognized association type ${association.type}".toString())
        }
    }

}

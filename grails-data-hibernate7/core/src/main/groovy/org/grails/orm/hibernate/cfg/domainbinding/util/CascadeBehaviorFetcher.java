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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import java.util.Map;
import java.util.Optional;

import org.hibernate.MappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.EmbeddedCollection;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.ALL;
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.NONE;
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.SAVE_UPDATE;

/**
 * The cascade behavior fetcher class.
 */
public class CascadeBehaviorFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(CascadeBehaviorFetcher.class);

    private final LogCascadeMapping logCascadeMapping;

    /**
     * Creates a new {@link CascadeBehaviorFetcher} instance.
     */
    public CascadeBehaviorFetcher(LogCascadeMapping logCascadeMapping) {
        this.logCascadeMapping = logCascadeMapping;
    }

    /**
     * Creates a new {@link CascadeBehaviorFetcher} instance.
     */
    public CascadeBehaviorFetcher() {
        this(new LogCascadeMapping(LOG));
    }

    /**
     * Gets the cascade behaviour.
     */
    public String getCascadeBehaviour(Association<?> association) {
        var cascadeStrategy =
                getDefinedBehavior((HibernatePersistentProperty) association).orElse(getImpliedBehavior(association));

        logCascadeMapping.logCascadeMapping(association, cascadeStrategy);

        return cascadeStrategy.getValue();
    }

    private Optional<CascadeBehavior> getDefinedBehavior(HibernatePersistentProperty grailsProperty) {
        return Optional.ofNullable(grailsProperty.getMappedForm())
                .map(PropertyConfig::getCascade)
                .map(CascadeBehavior::fromString);
    }

    private CascadeBehavior getImpliedBehavior(Association<?> association) {
        // Handle types that do not require an associated entity first
        if (association instanceof Basic) {
            return ALL;
        }

        if (Map.class.isAssignableFrom(association.getType())) {
            return association.isCorrectlyOwned() ? ALL : SAVE_UPDATE;
        }

        if (association instanceof Embedded) {
            return ALL;
        }

        if (association instanceof EmbeddedCollection) {
            return ALL;
        }

        // Fail-fast only for entity relationships that are truly missing an association
        if (association.getAssociatedEntity() == null) {
            throw new MappingException("Relationship " + association + " has no associated entity");
        }

        if (association.isHasOne()) {
            return ALL;
        } else if (association instanceof HibernateOneToOneProperty) {
            return association.isOwningSide() ? ALL : SAVE_UPDATE;
        } else if (association instanceof HibernateOneToManyProperty) {
            return association.isCorrectlyOwned() ? ALL : SAVE_UPDATE;
        } else if (association instanceof HibernateManyToManyProperty) {
            return association.isCorrectlyOwned() || association.isCircular() ? SAVE_UPDATE : NONE;
        } else if (association instanceof HibernateManyToOneProperty) {
            if (association.isCorrectlyOwned() && !association.isCircular()) {
                return ALL;
            } else if (association.isCompositeIdProperty()) {
                return ALL;
            } else {
                return NONE;
            }
        } else {
            throw new MappingException("Unrecognized association type " + association.getType());
        }
    }
}

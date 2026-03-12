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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import java.util.Collection;

import jakarta.annotation.Nonnull;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Subclass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder;

/** Binder for root classes. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class RootBinder {

    private static final Logger LOG = LoggerFactory.getLogger(RootBinder.class);

    private final String dataSourceName;
    private final MultiTenantFilterBinder multiTenantFilterBinder;
    private final SubClassBinder subClassBinder;
    private final RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder;
    private final DiscriminatorPropertyBinder discriminatorPropertyBinder;
    private final InFlightMetadataCollector mappings;

    public RootBinder(
            String dataSourceName,
            MultiTenantFilterBinder multiTenantFilterBinder,
            SubClassBinder subClassBinder,
            RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder,
            DiscriminatorPropertyBinder discriminatorPropertyBinder,
            InFlightMetadataCollector mappings) {
        this.dataSourceName = dataSourceName;
        this.multiTenantFilterBinder = multiTenantFilterBinder;
        this.subClassBinder = subClassBinder;
        this.rootPersistentClassCommonValuesBinder = rootPersistentClassCommonValuesBinder;
        this.discriminatorPropertyBinder = discriminatorPropertyBinder;
        this.mappings = mappings;
    }

    /**
     * Binds a root class (one with no super classes) to the runtime meta model based on the supplied
     * Grails domain class
     *
     * @param entity The Grails domain class
     */
    public void bindRoot(@Nonnull GrailsHibernatePersistentEntity entity) {
        if (mappings.getEntityBinding(entity.getName()) != null) {
            LOG.warn("[RootBinder] Class [" + entity.getName() + "] is already mapped, skipping.. ");
            return;
        }

        Collection<GrailsHibernatePersistentEntity> children = entity.getChildEntities(dataSourceName);
        RootClass root = rootPersistentClassCommonValuesBinder.bindRoot(entity);

        if (!children.isEmpty() && entity.isTablePerHierarchy()) {
            discriminatorPropertyBinder.bindDiscriminatorProperty(root);
        }

        // bind the sub classes
        children.stream()
                .flatMap(sub -> subClassBinder.bindSubClass(sub, root).stream())
                .forEach(mappings::addEntityBinding);

        multiTenantFilterBinder.bind(entity, root);

        mappings.addEntityBinding(root);
    }
}

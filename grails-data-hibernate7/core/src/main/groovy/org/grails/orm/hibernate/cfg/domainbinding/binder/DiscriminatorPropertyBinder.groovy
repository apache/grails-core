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

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.SimpleValue;

import org.grails.orm.hibernate.cfg.DiscriminatorConfig;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class DiscriminatorPropertyBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final MappingCacheHolder mappingCacheHolder;
    private final ConfiguredDiscriminatorBinder configuredDiscriminatorBinder;
    private final DefaultDiscriminatorBinder defaultDiscriminatorBinder;

    public DiscriminatorPropertyBinder(
            MetadataBuildingContext metadataBuildingContext,
            MappingCacheHolder mappingCacheHolder,
            ConfiguredDiscriminatorBinder configuredDiscriminatorBinder,
            DefaultDiscriminatorBinder defaultDiscriminatorBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.mappingCacheHolder = mappingCacheHolder;
        this.configuredDiscriminatorBinder = configuredDiscriminatorBinder;
        this.defaultDiscriminatorBinder = defaultDiscriminatorBinder;
    }

    /**
     * Creates and binds the discriminator property used in table-per-hierarchy inheritance to
     * discriminate between sub class instances
     *
     * @param entity The root class entity
     */
    public void bindDiscriminatorProperty(RootClass entity) {
        SimpleValue discriminator = createDiscriminator(entity);
        entity.setDiscriminator(discriminator);

        Mapping mapping = mappingCacheHolder.getMapping(entity.getMappedClass());
        DiscriminatorConfig config = mapping.getDiscriminator();

        if (config != null) {
            configuredDiscriminatorBinder.bindConfiguredDiscriminator(entity, discriminator, config);
        } else {
            defaultDiscriminatorBinder.bindDefaultDiscriminator(entity, discriminator);
        }
    }

    private SimpleValue createDiscriminator(RootClass entity) {
        return new BasicValue(metadataBuildingContext, entity.getTable());
    }
}

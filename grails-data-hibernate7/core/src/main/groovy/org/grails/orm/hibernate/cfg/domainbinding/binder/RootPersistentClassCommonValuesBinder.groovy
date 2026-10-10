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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.grails.orm.hibernate.cfg.CacheConfig
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity

@CompileStatic
class RootPersistentClassCommonValuesBinder {

    static final Logger LOG = LoggerFactory.getLogger(RootPersistentClassCommonValuesBinder)

    private final MetadataBuildingContext metadataBuildingContext
    private final PersistentEntityNamingStrategy namingStrategy
    private final IdentityBinder identityBinder
    private final VersionBinder versionBinder
    private final ClassBinder classBinder
    private final ClassPropertiesBinder classPropertiesBinder
    private final InFlightMetadataCollector mappings

    RootPersistentClassCommonValuesBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            IdentityBinder identityBinder,
            VersionBinder versionBinder,
            ClassBinder classBinder,
            ClassPropertiesBinder classPropertiesBinder,
            InFlightMetadataCollector mappings) {
        this.metadataBuildingContext = metadataBuildingContext
        this.namingStrategy = namingStrategy
        this.identityBinder = identityBinder
        this.versionBinder = versionBinder
        this.classBinder = classBinder
        this.classPropertiesBinder = classPropertiesBinder
        this.mappings = mappings
    }

    RootClass bindRoot(@Nonnull HibernatePersistentEntity hibernatePersistentEntity) {

        RootClass root = new RootClass(this.metadataBuildingContext)
        classBinder.bindClass(hibernatePersistentEntity, root)

        // get the schema and catalog names from the configuration
        Mapping gormMapping = hibernatePersistentEntity.hibernateMappedForm

        hibernatePersistentEntity.configureDerivedProperties()
        CacheConfig cc = gormMapping.cache
        if (cc != null && cc.enabled) {
            root.cacheConcurrencyStrategy = cc.usage.toString()
            root.cached = true
            if ('read-only'.equalsIgnoreCase(cc.usage.toString())) {
                root.mutable = false
            }
            root.setLazyPropertiesCacheable(
                    !'non-lazy'.equalsIgnoreCase(cc.include.toString()))
        }

        String schema = hibernatePersistentEntity.getSchema(mappings)

        String catalog = hibernatePersistentEntity.getCatalog(mappings)

        // create the table
        Table table = mappings.addTable(
                schema,
                catalog,
                hibernatePersistentEntity.getTableName(namingStrategy),
                null,
                hibernatePersistentEntity.isTableAbstract(),
                metadataBuildingContext,
                false)
        root.table = table
        if (LOG.isDebugEnabled()) {
            LOG.debug('[GrailsDomainBinder] Mapping Grails domain class: {} -> {}', hibernatePersistentEntity.name, root.table.name)
        }

        identityBinder.bindIdentity(hibernatePersistentEntity)
        versionBinder.bindVersion(hibernatePersistentEntity.version, root)
        root.createPrimaryKey()
        classPropertiesBinder.bindClassProperties(hibernatePersistentEntity)

        return root
    }

}

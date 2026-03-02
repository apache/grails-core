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

import jakarta.annotation.Nonnull;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.RootClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.orm.hibernate.cfg.CacheConfig;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;

public class RootPersistentClassCommonValuesBinder {
    public static final Logger LOG = LoggerFactory.getLogger(RootPersistentClassCommonValuesBinder.class);

  private final MetadataBuildingContext metadataBuildingContext;
  private final PersistentEntityNamingStrategy namingStrategy;
  private final IdentityBinder identityBinder;
  private final VersionBinder versionBinder;
  private final ClassBinder classBinder;
  private final ClassPropertiesBinder classPropertiesBinder;
  private final InFlightMetadataCollector mappings;

  public RootPersistentClassCommonValuesBinder(
      MetadataBuildingContext metadataBuildingContext,
      PersistentEntityNamingStrategy namingStrategy,
      IdentityBinder identityBinder,
      VersionBinder versionBinder,
      ClassBinder classBinder,
      ClassPropertiesBinder classPropertiesBinder,
      InFlightMetadataCollector mappings) {
    this.metadataBuildingContext = metadataBuildingContext;
    this.namingStrategy = namingStrategy;
    this.identityBinder = identityBinder;
    this.versionBinder = versionBinder;
    this.classBinder = classBinder;
    this.classPropertiesBinder = classPropertiesBinder;
    this.mappings = mappings;
  }

  public RootClass bindRootPersistentClassCommonValues(
      @Nonnull GrailsHibernatePersistentEntity domainClass,
      @Nonnull Collection<GrailsHibernatePersistentEntity> children) {

    RootClass root = new RootClass(this.metadataBuildingContext);
    root.setAbstract(domainClass.isAbstract());
    classBinder.bindClass(domainClass, root);

    // get the schema and catalog names from the configuration
    Mapping gormMapping = domainClass.getMappedForm();

    domainClass.configureDerivedProperties();
    CacheConfig cc = gormMapping.getCache();
    if (cc != null && cc.getEnabled()) {
      root.setCacheConcurrencyStrategy(cc.getUsage());
      root.setCached(true);
      if ("read-only".equals(cc.getUsage())) {
        root.setMutable(false);
      }
      root.setLazyPropertiesCacheable(!"non-lazy".equals(cc.getInclude()));
    }
    root.setBatchSize(ofNullable(gormMapping.getBatchSize()).orElse(0));
    root.setDynamicUpdate(gormMapping.getDynamicUpdate());
    root.setDynamicInsert(gormMapping.getDynamicInsert());

    var schema = domainClass.getSchema(mappings);

    var catalog = domainClass.getCatalog(mappings);

    // create the table
    var table =
        mappings.addTable(
            schema,
            catalog,
            domainClass.getTableName(namingStrategy),
            null,
            domainClass.isTableAbstract(),
            metadataBuildingContext);
    root.setTable(table);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "[GrailsDomainBinder] Mapping Grails domain class: "
              + domainClass.getName()
              + " -> "
              + root.getTable().getName());
    }

    public RootClass bindRoot(@Nonnull HibernatePersistentEntity hibernatePersistentEntity) {

        RootClass root = new RootClass(this.metadataBuildingContext);
        classBinder.bindClass(hibernatePersistentEntity, root);

        // get the schema and catalog names from the configuration
        Mapping gormMapping = hibernatePersistentEntity.getMappedForm();

        hibernatePersistentEntity.configureDerivedProperties();
        CacheConfig cc = gormMapping.getCache();
        if (cc != null && cc.getEnabled()) {
            root.setCacheConcurrencyStrategy(cc.getUsage().toString());
            root.setCached(true);
            if ("read-only".equalsIgnoreCase(cc.getUsage().toString())) {
                root.setMutable(false);
            }
            root.setLazyPropertiesCacheable(!"non-lazy".equalsIgnoreCase(cc.getInclude().toString()));
        }

        var schema = hibernatePersistentEntity.getSchema(mappings);

        var catalog = hibernatePersistentEntity.getCatalog(mappings);

        // create the table
        var table = mappings.addTable(
                schema,
                catalog,
                hibernatePersistentEntity.getTableName(namingStrategy),
                null,
                hibernatePersistentEntity.isTableAbstract(),
                metadataBuildingContext);
        root.setTable(table);
        if (LOG.isDebugEnabled()) {
            LOG.debug("[GrailsDomainBinder] Mapping Grails domain class: " +
                    hibernatePersistentEntity.getName() +
                    " -> " +
                    root.getTable().getName());
        }

        identityBinder.bindIdentity(hibernatePersistentEntity, root);
        versionBinder.bindVersion(hibernatePersistentEntity.getVersion(), root);
        root.createPrimaryKey();
        classPropertiesBinder.bindClassProperties(hibernatePersistentEntity);

        return root;
    }
}

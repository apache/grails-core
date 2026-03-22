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

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UnionSubclass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * Binds a union sub-class mapping using table-per-concrete-class
 *
 * @since 7.0
 */
public class UnionSubclassBinder {

    private static final Logger LOG = LoggerFactory.getLogger(UnionSubclassBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final ClassBinder classBinder;
    private final InFlightMetadataCollector mappings;

    public UnionSubclassBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            ClassBinder classBinder,
            InFlightMetadataCollector mappings) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.classBinder = classBinder;
        this.mappings = mappings;
    }

    /**
     * Binds a union sub-class mapping using table-per-concrete-class
     *
     * @param subClass The Grails sub class
     * @param parent The Hibernate Parent PersistentClass object
     * @return The created UnionSubclass
     */
    public UnionSubclass bindUnionSubclass(@Nonnull GrailsHibernatePersistentEntity subClass, PersistentClass parent)
            throws MappingException {
        UnionSubclass unionSubclass = new UnionSubclass(parent, metadataBuildingContext);
        classBinder.bindClass(subClass, unionSubclass);

        String schema = subClass.getSchema(mappings);
        String catalog = subClass.getCatalog(mappings);

        Table denormalizedSuperTable = unionSubclass.getSuperclass().getTable();
        Table mytable = mappings.addDenormalizedTable(
                schema,
                catalog,
                subClass.getTableName(namingStrategy),
                Boolean.TRUE.equals(unionSubclass.isAbstract()),
                null,
                denormalizedSuperTable,
                metadataBuildingContext);
        unionSubclass.setTable(mytable);
        unionSubclass.setClassName(subClass.getName());

        if (LOG.isInfoEnabled()) {
            LOG.info("Mapping union-subclass: " + unionSubclass.getEntityName()
                    + " -> "
                    + unionSubclass.getTable().getName());
        }
        return unionSubclass;
    }
}

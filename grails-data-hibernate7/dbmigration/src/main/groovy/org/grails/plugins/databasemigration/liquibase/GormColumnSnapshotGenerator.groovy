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
package org.grails.plugins.databasemigration.liquibase

import groovy.transform.CompileStatic
import liquibase.database.Database
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.SnapshotGenerator
import liquibase.snapshot.SnapshotGeneratorChain
import liquibase.structure.DatabaseObject
import liquibase.structure.core.Column
import liquibase.structure.core.Relation
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.Identity
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SimpleValue
import org.hibernate.mapping.Selectable
import org.hibernate.boot.Metadata

@CompileStatic
class GormColumnSnapshotGenerator implements SnapshotGenerator {

    @Override
    int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (database instanceof GormDatabase && Column.class.isAssignableFrom(objectType)) {
            return 10 + 100 // VERY HIGH PRIORITY
        }
        return -1 
    }

    @Override
    Class<? extends DatabaseObject>[] addsTo() {
        return [Column] as Class[]
    }

    @Override
    Class<? extends SnapshotGenerator>[] replaces() {
        return [] as Class[]
    }

    @Override
    public <T extends DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot, SnapshotGeneratorChain chain) {
        T snapshotObject = chain.snapshot(example, snapshot)

        if (snapshotObject instanceof Column && snapshot.getDatabase() instanceof GormDatabase) {
            Column column = (Column) snapshotObject
            
            Relation relation = column.getRelation()
            if (relation == null) return snapshotObject
            String tableName = relation.getName()
            if (tableName == null) return snapshotObject

            GormDatabase gormDb = (GormDatabase) snapshot.getDatabase()
            def gormDatastore = gormDb.getGormDatastore()
            if (gormDatastore == null) return snapshotObject
            
            MappingContext mappingContext = gormDatastore.getMappingContext()
            Metadata metadata = gormDb.getMetadata()
            if (metadata == null) return snapshotObject
            
            for (PersistentClass pc : metadata.getEntityBindings()) {
                if (pc instanceof RootClass) {
                    RootClass root = (RootClass) pc
                    if (tableName.equalsIgnoreCase(root.getTable()?.getName())) {
                        org.hibernate.mapping.Column hibernateColumn = null
                        for (org.hibernate.mapping.Column hc : root.getTable().getColumns()) {
                            if (hc.getName().equalsIgnoreCase(column.getName())) {
                                hibernateColumn = hc
                                break
                            }
                        }
                        
                        if (hibernateColumn != null) {
                            PersistentEntity entity = mappingContext.getPersistentEntity(pc.getClassName() ?: pc.getEntityName())
                            if (entity instanceof GrailsHibernatePersistentEntity) {
                                GrailsHibernatePersistentEntity gpe = (GrailsHibernatePersistentEntity) entity
                                
                                // 1. Check if it is an ID column
                                boolean isIdColumn = false
                                if (root.getIdentifier() instanceof SimpleValue) {
                                    SimpleValue sv = (SimpleValue) root.getIdentifier()
                                    for (Selectable s : sv.getColumns()) {
                                        if (s instanceof org.hibernate.mapping.Column) {
                                            if (s.getName().equalsIgnoreCase(column.getName())) {
                                                isIdColumn = true
                                                break
                                            }
                                        }
                                    }
                                }

                                if (isIdColumn) {
                                    // Always set identifiers as non-nullable
                                    column.setNullable(false)
                                    
                                    Mapping m = gpe.getMappedForm()
                                    Object idMapping = m.getIdentity()
                                    if (idMapping instanceof Identity) {
                                        Identity identity = (Identity) idMapping
                                        boolean useSequence = m.isTablePerConcreteClass()
                                        String strategy = identity.determineGeneratorName(useSequence)
                                        if ("identity" == strategy || "native" == strategy || "sequence-identity" == strategy) {
                                            column.setAutoIncrementInformation(new Column.AutoIncrementInformation())
                                        }
                                    }
                                } else {
                                    // 2. Fix nullability for non-ID columns using GORM metadata
                                    if (column.isNullable() == null || column.isNullable()) {
                                        boolean gormNullable = true
                                        for (PersistentProperty prop : gpe.getPersistentProperties()) {
                                            String propColumnName = null
                                            if (prop instanceof GrailsHibernatePersistentProperty) {
                                                propColumnName = ((GrailsHibernatePersistentProperty) prop).getMappedColumnName()
                                            }
                                            if (propColumnName == null) {
                                                // Default naming convention
                                                propColumnName = prop.getName()
                                                if (prop instanceof org.grails.datastore.mapping.model.types.Association) {
                                                    propColumnName += "_id"
                                                }
                                            }
                                            
                                            if (column.getName().equalsIgnoreCase(propColumnName)) {
                                                gormNullable = prop.isNullable()
                                                break
                                            }
                                        }
                                        
                                        if (!gormNullable) {
                                            column.setNullable(false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return snapshotObject
    }
}

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
import org.grails.datastore.mapping.model.types.Association
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

        if (!(snapshotObject instanceof Column) || !(snapshot.database instanceof GormDatabase)) {
            return snapshotObject
        }

        Column column = (Column) snapshotObject
        String tableName = column.relation?.name
        if (!tableName) return snapshotObject

        GormDatabase gormDb = (GormDatabase) snapshot.database
        def gormDatastore = gormDb.gormDatastore
        if (!gormDatastore) return snapshotObject

        PersistentClass pc = findPersistentClass(gormDb.metadata, tableName)
        if (!pc) return snapshotObject

        MappingContext mappingContext = gormDatastore.mappingContext
        PersistentEntity entity = mappingContext.getPersistentEntity(pc.className ?: pc.entityName)
        if (!(entity instanceof GrailsHibernatePersistentEntity)) return snapshotObject

        GrailsHibernatePersistentEntity gpe = (GrailsHibernatePersistentEntity) entity

        if (isIdentifier(pc, column.name)) {
            applyGormIdentitySettings(column, gpe)
        } else {
            PersistentProperty prop = resolveGormProperty(gpe, column.name)
            if (prop) {
                applyGormPropertySettings(column, prop)
            }
        }

        return snapshotObject
    }

    protected PersistentClass findPersistentClass(Metadata metadata, String tableName) {
        for (PersistentClass pc : metadata.entityBindings) {
            if (tableName.equalsIgnoreCase(pc.table?.name)) {
                return pc
            }
        }
        return null
    }

    protected boolean isIdentifier(PersistentClass pc, String columnName) {
        if (!(pc instanceof RootClass)) return false
        RootClass root = (RootClass) pc
        if (!(root.identifier instanceof SimpleValue)) return false
        SimpleValue sv = (SimpleValue) root.identifier
        return sv.columns.any { Selectable s ->
            s instanceof org.hibernate.mapping.Column && s.name.equalsIgnoreCase(columnName)
        }
    }

    protected PersistentProperty resolveGormProperty(GrailsHibernatePersistentEntity gpe, String columnName) {
        for (PersistentProperty prop : gpe.hibernatePersistentProperties) {
            String propColumnName = null
            if (prop instanceof GrailsHibernatePersistentProperty) {
                propColumnName = ((GrailsHibernatePersistentProperty) prop).mappedColumnName
            }
            if (propColumnName == null) {
                propColumnName = prop.name
                if (prop instanceof Association) {
                    propColumnName += "_id"
                }
            }
            if (columnName.equalsIgnoreCase(propColumnName)) {
                return prop
            }
        }
        return null
    }

    protected void applyGormIdentitySettings(Column column, GrailsHibernatePersistentEntity gpe) {
        // Always set identifiers as non-nullable
        column.setNullable(false)
        
        Mapping m = gpe.mappedForm
        Object idMapping = m.identity
        if (idMapping instanceof Identity) {
            Identity identity = (Identity) idMapping
            boolean useSequence = m.isTablePerConcreteClass()
            String strategy = identity.determineGeneratorName(useSequence)
            if (strategy == "identity" || strategy == "native" || strategy == "sequence-identity") {
                column.setAutoIncrementInformation(new Column.AutoIncrementInformation())
            }
        }
    }

    protected void applyGormPropertySettings(Column column, PersistentProperty prop) {
        if (column.isNullable() == null || column.isNullable()) {
            if (!prop.isNullable()) {
                column.setNullable(false)
            }
        }
    }
}

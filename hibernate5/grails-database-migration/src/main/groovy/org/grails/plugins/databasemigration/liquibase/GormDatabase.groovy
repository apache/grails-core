/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.databasemigration.liquibase

import groovy.transform.CompileStatic
import liquibase.database.DatabaseConnection
import liquibase.database.OfflineConnection
import liquibase.exception.DatabaseException
import liquibase.ext.hibernate.database.HibernateDatabase
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.JdbcDatabaseSnapshot
import liquibase.snapshot.SnapshotControl
import liquibase.structure.DatabaseObject
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.dialect.Dialect
import org.hibernate.service.ServiceRegistry

@CompileStatic
class GormDatabase extends HibernateDatabase {

    final String shortName = 'GORM'
    final String DefaultDatabaseProductName = 'getDefaultDatabaseProductName'

    private Dialect dialect
    private Metadata metadata
    DatabaseConnection connection

    GormDatabase() {
    }

    GormDatabase(Dialect dialect, ServiceRegistry serviceRegistry, HibernateDatastore hibernateDatastore) {
        this.dialect = dialect
        this.metadata = hibernateDatastore.getMetadata()
        SnapshotControl snapshotControl = new SnapshotControl(this, null, null)
        GormDatabase database = this
        OfflineConnection connection = new OfflineConnection("offline:gorm", null) {
            DatabaseSnapshot getSnapshot(DatabaseObject[] examples) {
                new JdbcDatabaseSnapshot(examples, database, snapshotControl)
            }
        }
        this.connection = connection
    }

    @Override
    Dialect getDialect() {
        dialect
    }

    /**
     * Return the hibernate {@link Metadata} used by this database.
     */
    @Override
    public Metadata getMetadata() {
        metadata
    }

    @Override
    protected void configureSources(MetadataSources sources) throws DatabaseException {
        //no op
    }


    @Override
    boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        return false
    }

}






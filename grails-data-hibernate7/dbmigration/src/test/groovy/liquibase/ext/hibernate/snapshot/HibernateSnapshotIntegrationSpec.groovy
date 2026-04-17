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
package liquibase.ext.hibernate.snapshot

import liquibase.ext.hibernate.database.HibernateDatabase
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.JdbcDatabaseSnapshot
import liquibase.structure.DatabaseObject
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.plugins.databasemigration.liquibase.GormDatabase
import org.hibernate.boot.Metadata
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Specification

abstract class HibernateSnapshotIntegrationSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore
    HibernateDatabase database
    DatabaseSnapshot snapshot
    Metadata metadata

    def setup() {
        Map config = [
                'hibernate.dialect'           : H2Dialect.class.getName(),
                'dataSource.url'              : 'jdbc:h2:mem:test;DB_CLOSE_DELAY=-1',
                'dataSource.driverClassName'  : 'org.h2.Driver',
                'dataSource.username'         : 'sa',
                'dataSource.password'         : '',
                'hibernate.hbm2ddl.auto'      : 'create-drop',
                'hibernate.integration.envers.enabled': false
        ]
        
        datastore = new HibernateDatastore(config, getEntityClasses() as Class[])
        metadata = datastore.getMetadata()
        
        database = new GormDatabase(new H2Dialect(), datastore)
        
        snapshot = new JdbcDatabaseSnapshot([] as DatabaseObject[], database)
    }

    abstract List<Class> getEntityClasses()
}

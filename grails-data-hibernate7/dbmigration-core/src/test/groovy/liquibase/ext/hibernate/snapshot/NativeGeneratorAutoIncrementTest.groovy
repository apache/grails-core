/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package liquibase.ext.hibernate.snapshot

import liquibase.CatalogAndSchema
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.ext.hibernate.database.HibernateSpringPackageDatabase
import liquibase.ext.hibernate.database.connection.HibernateConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.SnapshotControl
import liquibase.snapshot.SnapshotGeneratorFactory
import liquibase.structure.core.Column
import liquibase.structure.core.Schema
import liquibase.structure.core.Table
import org.hibernate.dialect.HSQLDialect
import org.junit.Test

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

/**
 * Verifies that @NativeGenerator columns are correctly detected as
 * auto-increment in the snapshot.
 */
class NativeGeneratorAutoIncrementTest {

    @Test
    void nativeGeneratorColumnIsAutoIncrement() throws Exception {
        String packages = 'com.example.ejb3.customid'
        Database database = new HibernateSpringPackageDatabase()
        database.setDefaultSchemaName('PUBLIC')
        database.setDefaultCatalogName('TESTDB')
        database.setConnection(new JdbcConnection(new HibernateConnection(
                'hibernate:spring:' + packages + '?dialect=' + HSQLDialect.name,
                new ClassLoaderResourceAccessor())))
        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance()
                .createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database))

        Table nativeGenTable = (Table) snapshot.get(
                new Table().setName('native_gen_entity').setSchema(new Schema()))
        assertNotNull('native_gen_entity table should exist', nativeGenTable)

        Column idColumn = nativeGenTable.getColumn('id')
        assertNotNull('id column should exist', idColumn)
        assertTrue('@NativeGenerator id column should be auto-increment',
                idColumn.isAutoIncrement())
    }

}

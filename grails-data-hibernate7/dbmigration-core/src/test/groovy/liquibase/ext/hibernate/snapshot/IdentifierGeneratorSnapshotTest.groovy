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
import liquibase.structure.core.Sequence
import liquibase.structure.core.Table
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQLDialect
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

class IdentifierGeneratorSnapshotTest {

    @Test
    void oracleNativeSequenceIsNotAutoIncrement() throws Exception {
        DatabaseSnapshot snapshot = snapshot('com.example.ejb3.customid', OracleDialect)

        Column column = idColumn(snapshot, 'native_gen_entity')
        assertFalse('Oracle native sequences are not identity columns', column.isAutoIncrement())
        assertNull('Oracle native sequences do not use PostgreSQL nextval defaults', column.getDefaultValue())
        assertTrue(sequenceNames(snapshot).contains('native_gen_seq'))
    }

    @Test
    void mysqlTableBackedGeneratorsAreNotSequences() throws Exception {
        DatabaseSnapshot snapshot = snapshot('com.example.ejb3.auction', MySQLDialect)

        assertTrue('MySQL sequence emulation must not produce Liquibase sequences', snapshot.get(Sequence).isEmpty())
    }

    @Test
    void qualifiedSequenceIsAddedOnceWithBareName() throws Exception {
        DatabaseSnapshot snapshot = snapshot('com.example.ejb3.qualifiedsequence', PostgreSQLDialect)

        assertEquals(['qs_seq'] as Set, sequenceNames(snapshot))
        assertEquals('Namespace and generator passes must not duplicate a sequence', 1, snapshot.get(Sequence).size())
        assertEquals('Qualified sequences stay in the synthetic schema so the default diff still emits them',
                'HIBERNATE', snapshot.get(Sequence).iterator().next().getSchema().getName())
        Column column = idColumn(snapshot, 'QualifiedSequenceEntity')
        assertFalse(column.isAutoIncrement())
        assertTrue(column.getDefaultValue().toString().contains("nextval('app.qs_seq'::regclass)"))
    }

    @Test
    void forcedTableGeneratorHasNoSequenceOrNextvalDefault() throws Exception {
        DatabaseSnapshot snapshot = snapshot('com.example.ejb3.forcedtable', PostgreSQLDialect)

        assertTrue(snapshot.get(Sequence).isEmpty())
        Column column = idColumn(snapshot, 'ForcedTableEntity')
        assertFalse(column.isAutoIncrement())
        assertNull(column.getDefaultValue())
    }

    private DatabaseSnapshot snapshot(String packages, Class<? extends Dialect> dialect) throws Exception {
        Database database = new HibernateSpringPackageDatabase()
        database.setDefaultSchemaName('PUBLIC')
        database.setDefaultCatalogName('TESTDB')
        database.setConnection(new JdbcConnection(new HibernateConnection(
                'hibernate:spring:' + packages + '?dialect=' + dialect.name,
                new ClassLoaderResourceAccessor())))
        return SnapshotGeneratorFactory.getInstance()
                .createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database))
    }

    private Column idColumn(DatabaseSnapshot snapshot, String tableName) {
        Table table = (Table) snapshot.get(new Table().setName(tableName).setSchema(new Schema()))
        assertNotNull('Identifier table must be snapshotted', table)
        Column column = table.getColumn('id')
        assertNotNull('Identifier column must be snapshotted', column)
        return column
    }

    private Set<String> sequenceNames(DatabaseSnapshot snapshot) {
        return snapshot.get(Sequence).collect { Sequence sequence -> sequence.getName() } as Set<String>
    }

}

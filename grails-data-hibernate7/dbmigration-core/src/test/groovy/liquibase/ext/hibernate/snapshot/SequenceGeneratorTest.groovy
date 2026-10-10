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

import java.math.BigInteger

import liquibase.CatalogAndSchema
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.ext.hibernate.database.HibernateSpringPackageDatabase
import liquibase.ext.hibernate.database.connection.HibernateConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.SnapshotControl
import liquibase.snapshot.SnapshotGeneratorFactory
import liquibase.structure.core.Sequence
import org.hibernate.dialect.PostgreSQLDialect
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

/**
 * Verifies that @SequenceGenerator-based sequences are captured in the snapshot.
 * This matches the UserC pattern.
 */
class SequenceGeneratorTest {

    @Test
    void sequenceGeneratorIsCaptured() throws Exception {
        String packages = 'com.example.ejb3.auction'
        Database database = new HibernateSpringPackageDatabase()
        database.setDefaultSchemaName('PUBLIC')
        database.setDefaultCatalogName('TESTDB')
        database.setConnection(new JdbcConnection(new HibernateConnection(
                'hibernate:spring:' + packages + '?dialect=' + PostgreSQLDialect.name,
                new ClassLoaderResourceAccessor())))
        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance()
                .createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database))

        Sequence itemSeq = null
        for (Sequence seq : snapshot.get(Sequence)) {
            if (seq.getName().equalsIgnoreCase('ITEM_SEQ')) {
                itemSeq = seq
            }
        }

        assertNotNull('ITEM_SEQ should be in snapshot', itemSeq)
        assertEquals('initialValue should be 1000', BigInteger.valueOf(1000), itemSeq.getStartValue())
        assertEquals('allocationSize should be 100', BigInteger.valueOf(100), itemSeq.getIncrementBy())
    }

}

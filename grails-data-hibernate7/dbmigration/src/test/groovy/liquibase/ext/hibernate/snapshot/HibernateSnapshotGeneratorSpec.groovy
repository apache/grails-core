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
import liquibase.snapshot.SnapshotControl
import org.hibernate.boot.spi.MetadataImplementor
import spock.lang.Specification

abstract class HibernateSnapshotGeneratorSpec extends Specification {

    DatabaseSnapshot snapshot = Mock()
    HibernateDatabase database = Mock()
    MetadataImplementor metadata = Mock()
    SnapshotControl snapshotControl = Mock()

    def setup() {
        snapshot.getDatabase() >> database
        snapshot.getSnapshotControl() >> snapshotControl
        database.getMetadata() >> metadata
    }
}

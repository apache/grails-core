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
package liquibase.ext.hibernate.snapshot;

import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Catalog;
import liquibase.structure.core.Schema;

/**
 * Hibernate doesn't really support Schemas, so just return the passed example back as if it had all the info it needed.
 */
public class SchemaSnapshotGenerator extends HibernateSnapshotGenerator {

    public SchemaSnapshotGenerator() {
        super(Schema.class, new Class[] {Catalog.class});
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        return new Schema(
                        snapshot.getDatabase().getDefaultCatalogName(),
                        snapshot.getDatabase().getDefaultSchemaName())
                .setDefault(true);
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        // Nothing to do
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {liquibase.snapshot.jvm.SchemaSnapshotGenerator.class};
    }
}

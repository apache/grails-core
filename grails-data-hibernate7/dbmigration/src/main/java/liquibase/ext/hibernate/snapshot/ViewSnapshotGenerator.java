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
import liquibase.structure.core.Schema;
import liquibase.structure.core.View;

/**
 * View snapshots are not supported from hibernate, but this class needs to be implemented in order to prevent the default ViewSnapshotGenerator from running.
 */
public class ViewSnapshotGenerator extends HibernateSnapshotGenerator {

    public ViewSnapshotGenerator() {
        super(View.class, new Class[] {Schema.class});
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        throw new DatabaseException("No views in Hibernate mapping");
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        // No views in Hibernate mapping
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {liquibase.snapshot.jvm.ViewSnapshotGenerator.class};
    }
}

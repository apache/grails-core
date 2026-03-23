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
package liquibase.ext.hibernate.database;

import liquibase.CatalogAndSchema;
import liquibase.database.Database;
import liquibase.integration.commandline.CommandLineUtils;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Mårten Svantesson
 */
public class JPAPersistenceDatabaseTest {
    @Test
    public void persistenceXML() throws Exception {
        String url = "jpa:persistence:META-INF/persistence.xml";
        Database database = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(this.getClass().getClassLoader()), url, null, null, null, null, null, false, false, null, null, null, null, null, null, null);

        assertNotNull(database);

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        HibernateEjb3DatabaseTest.assertEjb3HibernateMapped(snapshot);
    }
}

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
package liquibase.ext.hibernate.diff;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.database.core.PostgresDatabase;
import liquibase.diff.ObjectDifferences;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.ChangeGeneratorFactory;
import liquibase.ext.hibernate.database.HibernateSpringPackageDatabase;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.Index;
import liquibase.structure.core.PrimaryKey;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import liquibase.structure.core.UniqueConstraint;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unknown Hibernate index attributes must not cause changes, but concrete uniqueness mismatches must.
 */
public class HibernateIndexDifferenceSuppressionTest {

    private final Database hibernateDatabase = new HibernateSpringPackageDatabase();
    private final Database postgres = new PostgresDatabase();

    @Test
    public void indexDifferencesInUniqueAndUsingAreSuppressed() {
        Index index = index();
        ObjectDifferences differences = differences("unique", "using");

        Change[] changes = fixChanged(index, differences);

        assertNull("Liquibase reports a fully suppressed difference as no changes", changes);
    }

    @Test
    public void concreteBooleanUniqueDifferenceIsPreserved() {
        ObjectDifferences differences = differences("using");
        differences.addDifference("unique", Boolean.FALSE, Boolean.TRUE);

        Change[] changes = fixChanged(index().setUnique(false), differences);

        assertNotNull("A concrete uniqueness mismatch must produce changes", changes);
        assertTrue("A concrete uniqueness mismatch must produce changes", changes.length > 0);
    }

    @Test
    public void unknownUniqueAndUsingDifferencesAreSuppressed() {
        ObjectDifferences differences = differences("using");
        differences.addDifference("unique", null, Boolean.TRUE);

        assertNull("Unknown uniqueness must not produce changes", fixChanged(index(), differences));
    }

    @Test
    public void primaryKeyDifferencesInUsingAreSuppressed() {
        PrimaryKey primaryKey = new PrimaryKey().setName("item_pkey").setTable(new Table().setName("item"));
        ObjectDifferences differences = differences("using");

        Change[] changes = fixChanged(primaryKey, differences);

        assertNull("Liquibase reports a fully suppressed difference as no changes", changes);
    }

    @Test
    public void uniqueConstraintDifferencesInUsingAreSuppressed() {
        UniqueConstraint uniqueConstraint = new UniqueConstraint().setName("uc_item_name")
                .setRelation(new Table().setName("item"));
        ObjectDifferences differences = differences("using");

        Change[] changes = fixChanged(uniqueConstraint, differences);

        assertNull("Liquibase reports a fully suppressed difference as no changes", changes);
    }

    @Test
    public void suppressionOnlyAppliesWhenHibernateIsInvolved() {
        Index index = index();
        ObjectDifferences differences = differences("unique", "using");

        Change[] changes = ChangeGeneratorFactory.getInstance()
                .fixChanged(index, differences, new DiffOutputControl(), postgres, postgres);

        assertNotNull("A plain database-to-database diff must still report index changes", changes);
        assertTrue("A plain database-to-database diff must still report index changes", changes.length > 0);
    }

    private Index index() {
        Table table = new Table().setName("item");
        table.setSchema(new Schema("testdb", "public"));
        return new Index("idx_item_name").setRelation(table).addColumn(new Column("name"));
    }

    private Change[] fixChanged(DatabaseObject changed, ObjectDifferences differences) {
        return ChangeGeneratorFactory.getInstance()
                .fixChanged(changed, differences, new DiffOutputControl(), hibernateDatabase, postgres);
    }

    private ObjectDifferences differences(String... fields) {
        ObjectDifferences differences = new ObjectDifferences(new CompareControl());
        for (String field : fields) {
            differences.addDifference(field, "reference", "compared");
        }
        return differences;
    }
}

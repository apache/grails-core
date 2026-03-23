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
package liquibase.ext.hibernate.diff;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.ObjectDifferences;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.PrimaryKey;

/**
 * Hibernate doesn't know about all the variations that occur with primary keys, especially backing index stuff.
 * To prevent changing customized primary keys, we suppress this kind of changes from hibernate side.
 */
public class ChangedPrimaryKeyChangeGenerator
        extends liquibase.diff.output.changelog.core.ChangedPrimaryKeyChangeGenerator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (PrimaryKey.class.isAssignableFrom(objectType)) {
            return PRIORITY_ADDITIONAL;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Change[] fixChanged(
            DatabaseObject changedObject,
            ObjectDifferences differences,
            DiffOutputControl control,
            Database referenceDatabase,
            Database comparisonDatabase,
            ChangeGeneratorChain chain) {
        if (referenceDatabase instanceof HibernateDatabase || comparisonDatabase instanceof HibernateDatabase) {
            differences.removeDifference("unique");
            differences.removeDifference("validate");
            if (!differences.hasDifferences()) {
                return new Change[0];
            }
        }

        return super.fixChanged(changedObject, differences, control, referenceDatabase, comparisonDatabase, chain);
    }
}

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

import java.util.Objects;
import java.util.Set;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.Difference;
import liquibase.diff.ObjectDifferences;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Sequence;

/**
 * Hibernate manages sequences only by the name, startValue and incrementBy fields.
 * However, non-hibernate databases might return default values for other fields triggering false positives.
 */
public class HibernateChangedSequenceChangeGenerator
        extends liquibase.diff.output.changelog.core.ChangedSequenceChangeGenerator {

    private static final String FIELD_NAME = "name";
    private static final String FIELD_START_VALUE = "startValue";
    private static final String FIELD_INCREMENT_BY = "incrementBy";

    private static final Set<String> HIBERNATE_SEQUENCE_FIELDS = Set.of(FIELD_NAME, FIELD_START_VALUE, FIELD_INCREMENT_BY);
    
    // Default values used by Hibernate's SequenceStyleGenerator
    private static final String DEFAULT_INITIAL_VALUE = "1";
    private static final String DEFAULT_INCREMENT_SIZE = "50";
    private static final Set<String> DEFAULT_VALUES = Set.of(DEFAULT_INITIAL_VALUE, DEFAULT_INCREMENT_SIZE);

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        return Sequence.class.isAssignableFrom(objectType) ? PRIORITY_ADDITIONAL : PRIORITY_NONE;
    }

    @Override
    public Change[] fixChanged(
            DatabaseObject changedObject,
            ObjectDifferences differences,
            DiffOutputControl control,
            Database referenceDatabase,
            Database comparisonDatabase,
            ChangeGeneratorChain chain) {
        
        if (isHibernateRelated(referenceDatabase, comparisonDatabase)) {
            filterIrrelevantDifferences(differences, referenceDatabase, comparisonDatabase);
        }

        return super.fixChanged(changedObject, differences, control, referenceDatabase, comparisonDatabase, chain);
    }

    private void filterIrrelevantDifferences(ObjectDifferences differences, Database refDb, Database compDb) {
        differences.getDifferences().stream()
                .filter(diff -> isIgnoredField(diff) || isAdvancedIgnoredDifference(diff, refDb, compDb))
                .map(Difference::getField)
                .toList()
                .forEach(differences::removeDifference);
    }

    private boolean isIgnoredField(Difference diff) {
        return !HIBERNATE_SEQUENCE_FIELDS.contains(diff.getField());
    }

    private boolean isAdvancedIgnoredDifference(Difference diff, Database refDb, Database compDb) {
        String field = diff.getField();
        String refValue = Objects.toString(diff.getReferenceValue(), null);
        String compValue = Objects.toString(diff.getComparedValue(), null);

        if (FIELD_NAME.equals(field)) {
            return isCaseInsensitiveMatch(refValue, compValue, refDb, compDb);
        }

        if (FIELD_START_VALUE.equals(field) || FIELD_INCREMENT_BY.equals(field)) {
            return isDefaultOrNullMatch(refValue, compValue);
        }

        return false;
    }

    private boolean isCaseInsensitiveMatch(String v1, String v2, Database d1, Database d2) {
        return (!d1.isCaseSensitive() || !d2.isCaseSensitive()) && v1 != null && v1.equalsIgnoreCase(v2);
    }

    private boolean isDefaultOrNullMatch(String v1, String v2) {
        return (v1 == null && DEFAULT_VALUES.contains(v2)) || (v2 == null && DEFAULT_VALUES.contains(v1));
    }

    private boolean isHibernateRelated(Database d1, Database d2) {
        return d1 instanceof HibernateDatabase || d2 instanceof HibernateDatabase;
    }
}

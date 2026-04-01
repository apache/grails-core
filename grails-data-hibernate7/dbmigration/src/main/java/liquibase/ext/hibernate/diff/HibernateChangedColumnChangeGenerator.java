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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.Difference;
import liquibase.diff.ObjectDifferences;
import liquibase.diff.output.DiffOutputControl;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.statement.DatabaseFunction;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.DataType;

/**
 * Hibernate and database types tend to look different even though they are not.
 * The only change that we are handling it size change, and even for this one there are exceptions.
 */
public class HibernateChangedColumnChangeGenerator extends liquibase.diff.output.changelog.core.ChangedColumnChangeGenerator {

    private static final List<String> TYPES_TO_IGNORE_SIZE = List.of("TIMESTAMP", "TIME");

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        return Column.class.isAssignableFrom(objectType) ? PRIORITY_ADDITIONAL : PRIORITY_NONE;
    }

    @Override
    protected void handleTypeDifferences(Column column, ObjectDifferences differences, DiffOutputControl control, List<Change> changes, Database referenceDatabase, Database comparisonDatabase) {
        if (isHibernateRelated(referenceDatabase, comparisonDatabase)) {
            handleHibernateTypeDifferences(column, differences, control, changes, referenceDatabase, comparisonDatabase);
        } else {
            super.handleTypeDifferences(column, differences, control, changes, referenceDatabase, comparisonDatabase);
        }
    }

    private void handleHibernateTypeDifferences(Column column, ObjectDifferences differences, DiffOutputControl control, List<Change> changes, Database refDb, Database compDb) {
        if (shouldIgnoreSize(column)) return;

        Optional.ofNullable(differences.getDifference("type")).ifPresent(diff -> {
            filterIrrelevantDifferences(differences);
            super.handleTypeDifferences(column, differences, control, changes, refDb, compDb);
        });
    }

    private void filterIrrelevantDifferences(ObjectDifferences differences) {
        new ArrayList<>(differences.getDifferences()).stream()
                .filter(Predicate.not(this::isMeaningfulDifference))
                .forEach(diff -> differences.removeDifference(diff.getField()));
    }

    private boolean isMeaningfulDifference(Difference diff) {
        return diff.getReferenceValue() instanceof DataType refType && 
               diff.getComparedValue() instanceof DataType compType &&
               !isSizeEqualOrNull(refType.getColumnSize(), compType.getColumnSize());
    }

    @Override
    protected void handleDefaultValueDifferences(Column column, ObjectDifferences differences, DiffOutputControl control, List<Change> changes, Database referenceDatabase, Database comparisonDatabase) {
        if (!isHibernateRelated(referenceDatabase, comparisonDatabase)) {
            super.handleDefaultValueDifferences(column, differences, control, changes, referenceDatabase, comparisonDatabase);
            return;
        }

        if (isFunctionDefaultAddingToNull(differences)) return;

        Optional.ofNullable(differences.getDifference("defaultValue"))
                .ifPresent(d -> super.handleDefaultValueDifferences(column, differences, control, changes, referenceDatabase, comparisonDatabase));
    }

    private boolean shouldIgnoreSize(Column column) {
        return TYPES_TO_IGNORE_SIZE.stream().anyMatch(type -> type.equalsIgnoreCase(column.getType().getTypeName()));
    }

    private boolean isSizeEqualOrNull(Integer s1, Integer s2) {
        return s1 == null || s2 == null || s1.equals(s2);
    }

    private boolean isFunctionDefaultAddingToNull(ObjectDifferences differences) {
        return Optional.ofNullable(differences.getDifference("defaultValue"))
                .filter(d -> d.getReferenceValue() == null && d.getComparedValue() instanceof DatabaseFunction)
                .isPresent();
    }

    private boolean isHibernateRelated(Database d1, Database d2) {
        return d1 instanceof HibernateDatabase || d2 instanceof HibernateDatabase;
    }
}

package liquibase.ext.hibernate.diff

import groovy.transform.CompileStatic
import liquibase.change.Change
import liquibase.database.Database
import liquibase.diff.Difference
import liquibase.diff.ObjectDifferences
import liquibase.diff.output.DiffOutputControl
import liquibase.diff.output.changelog.core.ChangedColumnChangeGenerator
import liquibase.ext.hibernate.database.HibernateDatabase
import liquibase.statement.DatabaseFunction
import liquibase.structure.DatabaseObject
import liquibase.structure.core.Column
import liquibase.structure.core.DataType

/**
 * Hibernate and database types tend to look different even though they are not.
 * The only change that we are handling it size change, and even for this one there are exceptions.
 */
@CompileStatic
class HibernateChangedColumnChangeGenerator extends ChangedColumnChangeGenerator {

    private static final List<String> TYPES_TO_IGNORE_SIZE = List.of('TIMESTAMP', 'TIME')

    @Override
    int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        return Column.isAssignableFrom(objectType) ? PRIORITY_ADDITIONAL : PRIORITY_NONE
    }

    @Override
    protected void handleTypeDifferences(Column column, ObjectDifferences differences, DiffOutputControl control, List<Change> changes, Database referenceDatabase, Database comparisonDatabase) {
        if (isHibernateRelated(referenceDatabase, comparisonDatabase)) {
            handleHibernateTypeDifferences(column, differences, control, changes, referenceDatabase, comparisonDatabase)
        } else {
            super.handleTypeDifferences(column, differences, control, changes, referenceDatabase, comparisonDatabase)
        }
    }

    private void handleHibernateTypeDifferences(Column column, ObjectDifferences differences, DiffOutputControl control, List<Change> changes, Database refDb, Database compDb) {
        if (shouldIgnoreSize(column)) {
            return
        }

        if (differences.getDifference('type') != null) {
            filterIrrelevantDifferences(differences)
            super.handleTypeDifferences(column, differences, control, changes, refDb, compDb)
        }
    }

    private void filterIrrelevantDifferences(ObjectDifferences differences) {
        for (Difference diff : new ArrayList<Difference>(differences.getDifferences())) {
            if (!isMeaningfulDifference(diff)) {
                differences.removeDifference(diff.getField())
            }
        }
    }

    private boolean isMeaningfulDifference(Difference diff) {
        Object referenceValue = diff.getReferenceValue()
        Object comparedValue = diff.getComparedValue()
        return referenceValue instanceof DataType &&
               comparedValue instanceof DataType &&
               !isSizeEqualOrNull(((DataType) referenceValue).getColumnSize(), ((DataType) comparedValue).getColumnSize())
    }

    @Override
    protected void handleDefaultValueDifferences(Column column, ObjectDifferences differences, DiffOutputControl control, List<Change> changes, Database referenceDatabase, Database comparisonDatabase) {
        if (!isHibernateRelated(referenceDatabase, comparisonDatabase)) {
            super.handleDefaultValueDifferences(column, differences, control, changes, referenceDatabase, comparisonDatabase)
            return
        }

        if (isFunctionDefaultAddingToNull(differences)) {
            return
        }

        if (differences.getDifference('defaultValue') != null) {
            super.handleDefaultValueDifferences(column, differences, control, changes, referenceDatabase, comparisonDatabase)
        }
    }

    private boolean shouldIgnoreSize(Column column) {
        String typeName = column.getType().getTypeName()
        for (String type : TYPES_TO_IGNORE_SIZE) {
            if (type.equalsIgnoreCase(typeName)) {
                return true
            }
        }
        return false
    }

    private boolean isSizeEqualOrNull(Integer s1, Integer s2) {
        return s1 == null || s2 == null || s1.equals(s2)
    }

    private boolean isFunctionDefaultAddingToNull(ObjectDifferences differences) {
        Difference difference = differences.getDifference('defaultValue')
        return difference != null &&
                difference.getReferenceValue() == null &&
                difference.getComparedValue() instanceof DatabaseFunction
    }

    private boolean isHibernateRelated(Database d1, Database d2) {
        return d1 instanceof HibernateDatabase || d2 instanceof HibernateDatabase
    }

}

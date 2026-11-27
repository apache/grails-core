package liquibase.ext.hibernate.diff

import groovy.transform.CompileStatic
import liquibase.change.Change
import liquibase.database.Database
import liquibase.diff.ObjectDifferences
import liquibase.diff.output.DiffOutputControl
import liquibase.diff.output.changelog.ChangeGeneratorChain
import liquibase.diff.output.changelog.core.ChangedForeignKeyChangeGenerator
import liquibase.ext.hibernate.database.HibernateDatabase
import liquibase.structure.DatabaseObject
import liquibase.structure.core.ForeignKey

/**
 * Hibernate doesn't know about all the variations that occur with foreign keys but just whether the FK exists or not.
 * To prevent changing customized foreign keys, we suppress all foreign key changes from hibernate.
 */
@CompileStatic
class HibernateChangedForeignKeyChangeGenerator extends ChangedForeignKeyChangeGenerator {

    @Override
    int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (ForeignKey.isAssignableFrom(objectType)) {
            return PRIORITY_ADDITIONAL
        }
        return PRIORITY_NONE
    }

    @Override
    Change[] fixChanged(
            DatabaseObject changedObject,
            ObjectDifferences differences,
            DiffOutputControl control,
            Database referenceDatabase,
            Database comparisonDatabase,
            ChangeGeneratorChain chain) {
        if (referenceDatabase instanceof HibernateDatabase || comparisonDatabase instanceof HibernateDatabase) {
            differences.removeDifference('deleteRule')
            differences.removeDifference('updateRule')
            differences.removeDifference('validate')
            if (!differences.hasDifferences()) {
                return new Change[0]
            }
        }

        return super.fixChanged(changedObject, differences, control, referenceDatabase, comparisonDatabase, chain)
    }

}

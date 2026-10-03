package liquibase.ext.hibernate.diff;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.ObjectDifferences;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Index;

/**
 * Hibernate does not know every index attribute ({@code unique}, {@code using}), so those differences are
 * suppressed to prevent needless drop and recreate changes on every diff.
 */
public class HibernateChangedIndexChangeGenerator
        extends liquibase.diff.output.changelog.core.ChangedIndexChangeGenerator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        return Index.class.isAssignableFrom(objectType) ? PRIORITY_ADDITIONAL : PRIORITY_NONE;
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
            differences.removeDifference("using");
            if (!differences.hasDifferences()) {
                return new Change[0];
            }
        }
        return super.fixChanged(changedObject, differences, control, referenceDatabase, comparisonDatabase, chain);
    }
}

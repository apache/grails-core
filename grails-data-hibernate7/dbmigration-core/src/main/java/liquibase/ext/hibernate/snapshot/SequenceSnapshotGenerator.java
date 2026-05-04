package liquibase.ext.hibernate.snapshot;

import liquibase.exception.DatabaseException;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Sequence;

import java.math.BigInteger;

/**
 * Sequence snapshots are not yet supported, but this class needs to be implemented in order to prevent the default SequenceSnapshotGenerator from running.
 */
public class SequenceSnapshotGenerator extends HibernateSnapshotGenerator {

    public SequenceSnapshotGenerator() {
        super(Sequence.class, new Class[]{Schema.class});
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        return example;
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(Sequence.class)) {
            return;
        }

        if (foundObject instanceof Schema) {
            Schema schema = (Schema) foundObject;
            HibernateDatabase database = (HibernateDatabase) snapshot.getDatabase();
            for (org.hibernate.boot.model.relational.Namespace namespace : database.getMetadata().getDatabase().getNamespaces()) {
                for (org.hibernate.boot.model.relational.Sequence sequence : namespace.getSequences()) {
                    schema.addDatabaseObject(new Sequence()
                            .setName(sequence.getName().getSequenceName().getText())
                            .setSchema(schema)
                            .setStartValue(BigInteger.valueOf(sequence.getInitialValue()))
                            .setIncrementBy(BigInteger.valueOf(sequence.getIncrementSize()))
                    );
                }
            }
        }
    }



}

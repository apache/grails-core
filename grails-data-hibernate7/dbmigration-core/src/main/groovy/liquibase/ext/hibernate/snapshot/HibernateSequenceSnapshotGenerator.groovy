package liquibase.ext.hibernate.snapshot

import groovy.transform.CompileStatic
import liquibase.exception.DatabaseException
import liquibase.ext.hibernate.database.HibernateDatabase
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.InvalidExampleException
import liquibase.snapshot.SnapshotGenerator
import liquibase.snapshot.jvm.SequenceSnapshotGenerator
import liquibase.structure.DatabaseObject
import liquibase.structure.core.Schema
import liquibase.structure.core.Sequence
import org.hibernate.boot.model.relational.Namespace

/**
 * Sequence snapshots are not yet supported, but this class needs to be implemented in order to prevent the default SequenceSnapshotGenerator from running.
 */
@CompileStatic
class HibernateSequenceSnapshotGenerator extends HibernateSnapshotGenerator {

    HibernateSequenceSnapshotGenerator() {
        super(Sequence, Schema)
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        return example
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(Sequence)) {
            return
        }

        if (foundObject instanceof Schema) {
            Schema schema = (Schema) foundObject
            HibernateDatabase database = (HibernateDatabase) snapshot.getDatabase()
            for (Namespace namespace : database.getMetadata().getDatabase().getNamespaces()) {
                for (org.hibernate.boot.model.relational.Sequence sequence : namespace.getSequences()) {
                    Sequence liquibaseSequence = new Sequence()
                    liquibaseSequence.setName(sequence.getName().getSequenceName().getText())
                    liquibaseSequence.setSchema(schema)
                    liquibaseSequence.setStartValue(BigInteger.valueOf(sequence.getInitialValue()))
                    liquibaseSequence.setIncrementBy(BigInteger.valueOf(sequence.getIncrementSize()))
                    schema.addDatabaseObject(liquibaseSequence)
                }
            }
        }
    }

    @Override
    Class<? extends SnapshotGenerator>[] replaces() {
        return [SequenceSnapshotGenerator] as Class<? extends SnapshotGenerator>[]
    }

}

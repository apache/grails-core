package liquibase.ext.hibernate.snapshot

import groovy.transform.CompileStatic
import liquibase.exception.DatabaseException
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.InvalidExampleException
import liquibase.snapshot.SnapshotGenerator
import liquibase.snapshot.jvm.ViewSnapshotGenerator
import liquibase.structure.DatabaseObject
import liquibase.structure.core.Schema
import liquibase.structure.core.View

/**
 * View snapshots are not supported from hibernate, but this class needs to be implemented in order to prevent the default ViewSnapshotGenerator from running.
 */
@CompileStatic
class HibernateViewSnapshotGenerator extends HibernateSnapshotGenerator {

    HibernateViewSnapshotGenerator() {
        super(View, Schema)
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        throw new DatabaseException('No views in Hibernate mapping')
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        // No views in Hibernate mapping
    }

    @Override
    Class<? extends SnapshotGenerator>[] replaces() {
        return [ViewSnapshotGenerator] as Class<? extends SnapshotGenerator>[]
    }

}

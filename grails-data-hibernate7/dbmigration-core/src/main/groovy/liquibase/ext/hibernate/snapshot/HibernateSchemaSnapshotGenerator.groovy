package liquibase.ext.hibernate.snapshot

import groovy.transform.CompileStatic
import liquibase.exception.DatabaseException
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.InvalidExampleException
import liquibase.snapshot.SnapshotGenerator
import liquibase.snapshot.jvm.SchemaSnapshotGenerator
import liquibase.structure.DatabaseObject
import liquibase.structure.core.Catalog
import liquibase.structure.core.Schema

/**
 * Hibernate doesn't really support Schemas, so just return the passed example back as if it had all the info it needed.
 */
@CompileStatic
class HibernateSchemaSnapshotGenerator extends HibernateSnapshotGenerator {

    HibernateSchemaSnapshotGenerator() {
        super(Schema, Catalog)
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        Schema schema = new Schema(
                snapshot.getDatabase().getDefaultCatalogName(),
                snapshot.getDatabase().getDefaultSchemaName())
        schema.setDefault(true)
        return schema
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        // Nothing to do
    }

    @Override
    Class<? extends SnapshotGenerator>[] replaces() {
        return [SchemaSnapshotGenerator] as Class<? extends SnapshotGenerator>[]
    }

}

package liquibase.ext.hibernate.snapshot

import groovy.transform.CompileStatic
import liquibase.exception.DatabaseException
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.InvalidExampleException
import liquibase.snapshot.SnapshotGenerator
import liquibase.snapshot.jvm.CatalogSnapshotGenerator
import liquibase.structure.DatabaseObject
import liquibase.structure.core.Catalog

/**
 * Hibernate doesn't really support Catalogs, so just return the passed example back as if it had all the info it needed.
 */
@CompileStatic
class HibernateCatalogSnapshotGenerator extends HibernateSnapshotGenerator {

    HibernateCatalogSnapshotGenerator() {
        super(Catalog)
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        Catalog catalog = new Catalog(snapshot.getDatabase().getDefaultCatalogName())
        catalog.setDefault(true)
        return catalog
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        // Nothing to add to
    }

    @Override
    Class<? extends SnapshotGenerator>[] replaces() {
        return [CatalogSnapshotGenerator] as Class<? extends SnapshotGenerator>[]
    }

}

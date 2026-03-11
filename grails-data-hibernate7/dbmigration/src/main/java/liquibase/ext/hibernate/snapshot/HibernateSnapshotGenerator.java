package liquibase.ext.hibernate.snapshot;

import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorChain;
import liquibase.structure.DatabaseObject;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.MetadataImplementor;

/**
 * Base class for all Hibernate SnapshotGenerators
 */
public abstract class HibernateSnapshotGenerator implements SnapshotGenerator {

    private static final int PRIORITY_HIBERNATE_ADDITIONAL = 200;
    private static final int PRIORITY_HIBERNATE_DEFAULT = 100;

    private Class<? extends DatabaseObject> defaultFor = null;
    private Class<? extends DatabaseObject>[] addsTo = null;

    protected HibernateSnapshotGenerator(Class<? extends DatabaseObject> defaultFor) {
        this.defaultFor = defaultFor;
    }

    protected HibernateSnapshotGenerator(
            Class<? extends DatabaseObject> defaultFor, Class<? extends DatabaseObject>[] addsTo) {
        this.defaultFor = defaultFor;
        this.addsTo = addsTo;
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return null;
    }

    @Override
    public final int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (database instanceof HibernateDatabase) {
            if (defaultFor != null && defaultFor.isAssignableFrom(objectType)) {
                return PRIORITY_HIBERNATE_DEFAULT;
            }
            if (addsTo() != null) {
                for (Class<? extends DatabaseObject> type : addsTo()) {
                    if (type.isAssignableFrom(objectType)) {
                        return PRIORITY_HIBERNATE_ADDITIONAL;
                    }
                }
            }
        }
        return PRIORITY_NONE;
    }

    @Override
    public final Class<? extends DatabaseObject>[] addsTo() {
        return addsTo;
    }

    @Override
    public final DatabaseObject snapshot(
            DatabaseObject example, DatabaseSnapshot snapshot, SnapshotGeneratorChain chain)
            throws DatabaseException, InvalidExampleException {
        if (defaultFor != null && defaultFor.isAssignableFrom(example.getClass())) {
            DatabaseObject result = snapshotObject(example, snapshot);
            return result;
        }
        DatabaseObject chainResponse = chain.snapshot(example, snapshot);
        if (chainResponse == null) {
            return null;
        }
        if (addsTo() != null) {
            for (Class<? extends DatabaseObject> addType : addsTo()) {
                if (addType.isAssignableFrom(example.getClass())) {
                    addTo(chainResponse, snapshot);
                }
            }
        }
        return chainResponse;
    }

    protected abstract DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException;

    protected abstract void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException;

    protected org.hibernate.mapping.Table findHibernateTable(DatabaseObject example, DatabaseSnapshot snapshot) {
        Metadata metadata = null;
        Database database = snapshot.getDatabase();
        if (database instanceof HibernateDatabase hibernateDatabase) {
            metadata = hibernateDatabase.getMetadata();
        } else {
            try {
                metadata = (Metadata) database.getClass().getMethod("getMetadata").invoke(database);
            } catch (Exception e) {
                // not a metadata-bearing database
            }
        }

        if (metadata == null) {
            return null;
        }

        MetadataImplementor metadataImplementor = (MetadataImplementor) metadata;

        for (var hibernateTable : metadataImplementor.collectTableMappings()) {
            if (hibernateTable.getName().equalsIgnoreCase(example.getName())) {
                return hibernateTable;
            }
        }

        for (var namespace : metadataImplementor.getDatabase().getNamespaces()) {
            for (var hibernateTable : namespace.getTables()) {
                if (hibernateTable.getName().equalsIgnoreCase(example.getName())) {
                    return hibernateTable;
                }
            }
        }

        return null;
    }
}

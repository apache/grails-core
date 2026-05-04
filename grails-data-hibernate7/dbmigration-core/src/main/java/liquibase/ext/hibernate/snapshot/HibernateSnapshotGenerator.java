package liquibase.ext.hibernate.snapshot;

import java.lang.reflect.Method;
import java.util.Arrays;

import liquibase.Scope;
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

    private final Class<? extends DatabaseObject> defaultFor;
    private final Class<? extends DatabaseObject>[] addsToTypes;

    @SuppressWarnings("unchecked")
    protected HibernateSnapshotGenerator(Class<? extends DatabaseObject> defaultFor) {
        this(defaultFor, (Class<? extends DatabaseObject>[]) new Class[0]);
    }

    @SafeVarargs
    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    protected HibernateSnapshotGenerator(
            Class<? extends DatabaseObject> defaultFor, Class<? extends DatabaseObject>... addsToTypes) {
        this.defaultFor = defaultFor;
        this.addsToTypes = addsToTypes == null ? null : Arrays.copyOf(addsToTypes, addsToTypes.length);
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[0];
    }

    @Override
    public final int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (database instanceof HibernateDatabase) {
            if (defaultFor != null && defaultFor.isAssignableFrom(objectType)) {
                return PRIORITY_HIBERNATE_DEFAULT;
            }
            Class<? extends DatabaseObject>[] types = addsTo();
            if (types != null) {
                for (Class<? extends DatabaseObject> type : types) {
                    if (type.isAssignableFrom(objectType)) {
                        return PRIORITY_HIBERNATE_ADDITIONAL;
                    }
                }
            }
        }
        return PRIORITY_NONE;
    }

    @Override
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public final Class<? extends DatabaseObject>[] addsTo() {
        return addsToTypes == null ? null : Arrays.copyOf(addsToTypes, addsToTypes.length);
    }

    @Override
    public final DatabaseObject snapshot(
            DatabaseObject example, DatabaseSnapshot snapshot, SnapshotGeneratorChain chain)
            throws DatabaseException, InvalidExampleException {
        if (defaultFor != null && defaultFor.isAssignableFrom(example.getClass())) {
            return snapshotObject(example, snapshot);
        }
        DatabaseObject chainResponse = chain.snapshot(example, snapshot);
        if (chainResponse == null) {
            return null;
        }
        Class<? extends DatabaseObject>[] types = addsTo();
        if (types != null) {
            for (Class<? extends DatabaseObject> addType : types) {
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

    @SuppressWarnings("PMD.CloseResource")
    protected org.hibernate.mapping.Table findHibernateTable(DatabaseObject example, DatabaseSnapshot snapshot) {
        Metadata metadata = null;
        Database database = snapshot.getDatabase();
        if (database instanceof HibernateDatabase hibernateDatabase) {
            metadata = hibernateDatabase.getMetadata();
        } else {
            try {
                Method getMetadata = database.getClass().getMethod("getMetadata");
                metadata = (Metadata) getMetadata.invoke(database);
            } catch (Exception e) {
                Scope.getCurrentScope().getLog(getClass()).debug("Error getting metadata from database", e);
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

package liquibase.ext.hibernate.snapshot;

import liquibase.Scope;
import liquibase.exception.DatabaseException;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.mapping.*;

public class TableSnapshotGenerator extends HibernateSnapshotGenerator {


    public TableSnapshotGenerator() {
        super(Table.class, new Class[]{Schema.class});
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        if (example.getSnapshotId() != null) {
            return example;
        }
        org.hibernate.mapping.Table hibernateTable = findHibernateTable(example, snapshot);
        if (hibernateTable == null) {
            return example;
        }

        Table table = new Table().setName(hibernateTable.getName());
        Scope.getCurrentScope().getLog(getClass()).info("Found table " + table.getName());
        table.setSchema(example.getSchema());
        if (hibernateTable.getComment() != null && !hibernateTable.getComment().isEmpty()) {
            table.setRemarks(hibernateTable.getComment());
        }

        return table;
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(Table.class)) {
            return;
        }

        if (foundObject instanceof Schema schema) {

            var database = (HibernateDatabase) snapshot.getDatabase();
            var metadata = (MetadataImplementor) database.getMetadata();

            // Hibernate 7: GenerationType.TABLE tables are not visible via getEntityBindings(),
            // so we retrieve tables from namespaces instead.
            for (Namespace namespace : metadata.getDatabase().getNamespaces()) {
                for (org.hibernate.mapping.Table hibernateTable : namespace.getTables()) {
                    if (hibernateTable.isPhysicalTable()) {
                        addDatabaseObjectToSchema(hibernateTable, schema, snapshot);
                        for (ForeignKey fk : hibernateTable.getForeignKeyCollection()) {
                            addDatabaseObjectToSchema(fk.getTable(), schema, snapshot);
                        }
                    }
                }
            }

            for (org.hibernate.mapping.Collection coll : metadata.getCollectionBindings()) {
                org.hibernate.mapping.Table hTable = coll.getCollectionTable();
                if (hTable.isPhysicalTable()) {
                    addDatabaseObjectToSchema(hTable, schema, snapshot);
                }
            }
        }
    }

    private void addDatabaseObjectToSchema(org.hibernate.mapping.Table join, Schema schema, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        Table joinTable = new Table().setName(join.getName());
        joinTable.setSchema(schema);
        Scope.getCurrentScope().getLog(getClass()).info("Found table " + joinTable.getName());
        schema.addDatabaseObject(snapshotObject(joinTable, snapshot));
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[]{liquibase.snapshot.jvm.TableSnapshotGenerator.class};
    }
}

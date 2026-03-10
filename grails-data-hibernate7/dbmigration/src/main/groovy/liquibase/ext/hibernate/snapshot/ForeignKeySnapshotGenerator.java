package liquibase.ext.hibernate.snapshot;

import liquibase.diff.compare.DatabaseObjectComparatorFactory;
import liquibase.exception.DatabaseException;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.ForeignKey;
import liquibase.structure.core.Table;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.mapping.Column;

import java.util.Collection;

public class ForeignKeySnapshotGenerator extends HibernateSnapshotGenerator {

    public ForeignKeySnapshotGenerator() {
        super(ForeignKey.class, new Class[]{Table.class});
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        return example;
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(ForeignKey.class)) {
            return;
        }
        if (foundObject instanceof Table table) {
            HibernateDatabase database = (HibernateDatabase) snapshot.getDatabase();
            MetadataImplementor metadata = (MetadataImplementor) database.getMetadata();

            Collection<org.hibernate.mapping.Table> tmapp = metadata.collectTableMappings();
            for (org.hibernate.mapping.Table hibernateTable : tmapp) {
                for (org.hibernate.mapping.ForeignKey hibernateForeignKey : hibernateTable.getForeignKeyCollection()) {
                    Table currentTable = new Table().setName(hibernateTable.getName());
                    currentTable.setSchema(hibernateTable.getCatalog(), hibernateTable.getSchema());

                    org.hibernate.mapping.Table hibernateReferencedTable = hibernateForeignKey.getReferencedTable();
                    Table referencedTable = new Table().setName(hibernateReferencedTable.getName());
                    referencedTable.setSchema(hibernateReferencedTable.getCatalog(), hibernateReferencedTable.getSchema());

                    if (hibernateForeignKey.isCreationEnabled() && hibernateForeignKey.isPhysicalConstraint()) {
                        ForeignKey fk = new ForeignKey();
                        fk.setName(hibernateForeignKey.getName());
                        fk.setPrimaryKeyTable(referencedTable);
                        fk.setForeignKeyTable(currentTable);
                        for (Column column : hibernateForeignKey.getColumns()) {
                            fk.addForeignKeyColumn(new liquibase.structure.core.Column(column.getName()));
                        }
                        for (Column column : hibernateForeignKey.getReferencedColumns()) {
                            fk.addPrimaryKeyColumn(new liquibase.structure.core.Column(column.getName()));
                        }
                        if (fk.getPrimaryKeyColumns() == null || fk.getPrimaryKeyColumns().isEmpty()) {
                            for (Column column : hibernateReferencedTable.getPrimaryKey().getColumns()) {
                                fk.addPrimaryKeyColumn(new liquibase.structure.core.Column(column.getName()));
                            }
                        }

                        fk.setDeferrable(false);
                        fk.setInitiallyDeferred(false);

//			Index index = new Index();
//			index.setName("IX_" + fk.getName());
//			index.setTable(fk.getForeignKeyTable());
//			index.setColumns(fk.getForeignKeyColumns());
//			fk.setBackingIndex(index);
//			table.getIndexes().add(index);

                        if (DatabaseObjectComparatorFactory.getInstance().isSameObject(currentTable, table, null, database)) {
                            table.getOutgoingForeignKeys().add(fk);
                            table.getSchema().addDatabaseObject(fk);
                        }
                    }
                }
            }
        }
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[]{ liquibase.snapshot.jvm.ForeignKeySnapshotGenerator.class };
    }

}

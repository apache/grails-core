package liquibase.ext.hibernate.snapshot;

import liquibase.Scope;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.*;

public class IndexSnapshotGenerator extends HibernateSnapshotGenerator {

    private static final String HIBERNATE_ORDER_ASC = "asc";
    private static final String HIBERNATE_ORDER_DESC = "desc";

    @SuppressWarnings("unchecked")
    public IndexSnapshotGenerator() {
        super(Index.class, new Class[]{Table.class, ForeignKey.class, UniqueConstraint.class});
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        if (example.getSnapshotId() != null) {
            return example;
        }
        Relation table = ((Index) example).getRelation();
        var hibernateTable = findHibernateTable(table, snapshot);
        if (hibernateTable == null) {
            return example;
        }
        for (var hibernateIndex : hibernateTable.getIndexes().values()) {
            Index index = handleHibernateIndex(table, hibernateIndex);
            if (index.getColumnNames().equalsIgnoreCase(((Index) example).getColumnNames())) {
                Scope.getCurrentScope().getLog(getClass()).info("Found index " + index.getName());
                table.getIndexes().add(index);
                return index;
            }
        }
        return example;

    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(Index.class)) {
            return;
        }
        if (foundObject instanceof Table table) {
            var hibernateTable = findHibernateTable(table, snapshot);
            if (hibernateTable == null) {
                return;
            }
            for (var hibernateIndex : hibernateTable.getIndexes().values()) {
                var index = handleHibernateIndex(table, hibernateIndex);
                Scope.getCurrentScope().getLog(getClass()).info("Found index " + index.getName());
                table.getIndexes().add(index);
            }
        }
    }

    private Index handleHibernateIndex(Relation table, org.hibernate.mapping.Index hibernateIndex) {
        Index index = new Index();
        index.setRelation(table);
        index.setName(hibernateIndex.getName());
        index.setUnique(isUniqueIndex(hibernateIndex));
        for (var selectable : hibernateIndex.getSelectables()) {
            org.hibernate.mapping.Column hibernateColumn = (org.hibernate.mapping.Column) selectable;
            String hibernateOrder = hibernateIndex.getSelectableOrderMap().get(hibernateColumn);
            Boolean descending = HIBERNATE_ORDER_ASC.equals(hibernateOrder)
                ? Boolean.FALSE
                : (HIBERNATE_ORDER_DESC.equals(hibernateOrder) ? Boolean.TRUE : null);
            index.getColumns().add(new Column(hibernateColumn.getName()).setRelation(table).setDescending(descending));
        }
        return index;
    }

    private Boolean isUniqueIndex(org.hibernate.mapping.Index hibernateIndex) {
        /*
        This seems to be necessary to explicitly tell liquibase that there's no
        actual diff in certain non-unique indexes
        */
        if (hibernateIndex.getColumnSpan() == 1) {
            var col = ((org.hibernate.mapping.Column) hibernateIndex.getSelectables().get(0));
            return col.isUnique();
        } else {
            /*
            It seems that because Hibernate does not implement the unique property of the Jpa composite index,
            the diff command appears 'difference', because the unique property of the entity index is 'null',
            and the value read from the database is 'false', resulting in the generated changeSet after the Drop and
            Recreate Index.
            */
            return false;
        }
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[]{liquibase.snapshot.jvm.IndexSnapshotGenerator.class};
    }
}

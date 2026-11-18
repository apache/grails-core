/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.MappingException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.ImplicitIndexNameSource;
import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.ImplicitUniqueKeyNameSource;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.IdentifierCollection;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;

/**
 * Gives every collection with a join table a uniqueness constraint of its own over its key column and the element,
 * so that a join table several collections of one owner share stays unique per collection.
 *
 * <p>Two unidirectional {@code hasMany} to the same class without a {@code joinTable} mapping share one join table,
 * with a key column for each of them and one element column. Hibernate creates the table's implicit unique key when
 * it creates the keys of the first collection, over that collection's key column and the element column, and creates
 * none for the others, as the table has a unique key already ({@code Set.createPrimaryKey}). This binder runs after
 * the keys of each collection are created and completes what is missing, in the shape the database needs
 * ({@link SharedJoinTableUniqueness}). A collection whose table is its own already has its constraint and is left as
 * Hibernate bound it, and so is a collection without a join table, whose key the other side owns, or whose element is
 * a large object, which cannot be indexed and which Hibernate leaves without a key too. It also restricts a
 * collection that shares a key column with another on that table to its own rows, which Hibernate's removal of all
 * the rows of a collection by its key would otherwise not (collections with separate key columns delete only their
 * own rows by key and need no restriction).</p>
 *
 * <p>Hibernate applies the restriction to the statements that load, count, join and delete the rows of a collection,
 * but loses it in the test of emptiness ({@code is empty}, {@code is not empty}); GORM translates that test as a size
 * test for a restricted many-to-many collection instead, through
 * {@link org.grails.orm.hibernate.query.RestrictedCollectionSqmTranslatorFactory} (apache/grails-core#16748), except in
 * an update or delete of an entity whose hierarchy spans several tables.</p>
 *
 * <p>A set cannot share a join table with a list or a map: the index column of a list or a map is not null, and the
 * rows of the set leave it null, so saving the set fails on the first insert.</p>
 *
 * @since 8.0.1
 */
public class SharedJoinTableUniqueKeyBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final SharedJoinTableUniqueness uniqueness;

    /** Resolves the uniqueness for the dialect of the metadata being built. */
    public SharedJoinTableUniqueKeyBinder(MetadataBuildingContext metadataBuildingContext) {
        this(metadataBuildingContext, metadataBuildingContext.getMetadataCollector().getDatabase().getDialect());
    }

    SharedJoinTableUniqueKeyBinder(MetadataBuildingContext metadataBuildingContext, Dialect dialect) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.uniqueness = SharedJoinTableUniqueness.forDialect(dialect);
    }

    /** The uniqueness this binder applies. */
    public SharedJoinTableUniqueness getUniqueness() {
        return uniqueness;
    }

    /**
     * Gives a set the uniqueness constraint over its key columns and its element on its join table. A list or a map
     * is keyed by its index, which its table has as part of the primary key, and a bag has no key at all; their
     * elements repeat by design, so they are left as Hibernate bound them.
     */
    public void bind(Collection collection) {
        Table table = collection.getCollectionTable();
        if (table == null || collection.isInverse() || collection.isOneToMany() || collection.getKey() == null) {
            return;
        }
        List<Collection> sharing = collectionsSharing(collection, table);
        restrictRowsToOwnCollection(sharing);
        if (collection instanceof IndexedCollection) {
            makeIndexedCollectionsShareable(sharing, table);
            return;
        }
        if (!collection.isSet()) {
            return;
        }
        List<Column> keyColumns = collection.getKey().getColumns();
        List<Column> elementColumns = columnsOf(collection);
        if (keyColumns.isEmpty() || elementColumns.isEmpty() || hasLobColumn(collection.getMetadata(), elementColumns)) {
            return;
        }
        uniqueness.apply(table, keyColumns, elementColumns, new ImplicitNaming(table));
    }

    /**
     * The collections that share the join table of the collection, the collection among them, or none when the table is
     * the collection's own. They are found among the bindings of the metadata; one that is not bound yet joins when its
     * own pass runs, which finds the others bound.
     */
    private List<Collection> collectionsSharing(Collection collection, Table table) {
        if (!hasColumnsOfOthers(collection, table)) {
            return List.of();
        }
        List<Collection> sharing = new ArrayList<>();
        for (Collection other : metadataBuildingContext.getMetadataCollector().getCollectionBindings()) {
            if (other.getCollectionTable() == table && !other.isInverse() && !other.isOneToMany() && other.getKey() != null) {
                sharing.add(other);
            }
        }
        return sharing.size() < 2 ? List.of() : sharing;
    }

    /**
     * Hibernate removes all the rows of a collection with one statement on its key, and loads them with one. On a join
     * table several collections share, a key column two of them use would take the rows of the other collection
     * along, so each collection that shares a key column with another on that table is restricted to the rows that have
     * a value in its own element columns. Sharing collections with separate key columns already select and delete
     * only their own rows by key, so they need no where restriction. The collections that share the table are found
     * among the bindings of the metadata; one whose element is not bound yet is restricted when its own pass runs,
     * which finds the others bound.
     */
    private void restrictRowsToOwnCollection(List<Collection> sharing) {
        Dialect dialect = metadataBuildingContext.getMetadataCollector().getDatabase().getDialect();
        for (Collection member : sharing) {
            if (!sharesKeyColumnWithOther(member, sharing)) {
                continue;
            }
            List<Column> columns = restrictingColumns(member, sharing);
            if (columns.isEmpty()) {
                continue;
            }
            List<String> present = new ArrayList<>();
            for (Column column : columns) {
                present.add(column.getQuotedName(dialect) + " is not null");
            }
            String restriction = String.join(" and ", present);
            String where = member.getWhere();
            if (where == null || where.isBlank()) {
                member.setWhere(restriction);
            } else if (!where.contains(restriction)) {
                member.setWhere("(" + where + ") and " + restriction);
            }
        }
    }

    private static List<Column> restrictingColumns(Collection member, List<Collection> sharing) {
        List<Column> columns = new ArrayList<>();
        if (member instanceof IndexedCollection indexed && indexed.getIndex() != null) {
            for (Selectable selectable : indexed.getIndex().getSelectables()) {
                if (selectable instanceof Column column && !columns.contains(column)) {
                    columns.add(column);
                }
            }
            Set<Column> otherElements = new HashSet<>();
            for (Collection other : sharing) {
                if (other != member) {
                    otherElements.addAll(columnsOf(other));
                }
            }
            for (Column column : columnsOf(member)) {
                if (!otherElements.contains(column) && !columns.contains(column)) {
                    columns.add(column);
                }
            }
            return columns;
        }
        return columnsOf(member);
    }

    /**
     * Hibernate keys the join table of a list or a map by the key and the index columns of the collection, with a
     * primary key over them ({@code IndexedCollection.createPrimaryKey}), which the next collection of a shared table
     * replaces with its own, and which makes them not null. A row of one collection leaves the columns of the others
     * null, so on a shared table that key fails the first insert of every collection but the last
     * (apache/grails-core#16700). Each list or map of the table loses that primary key, gets nullable key and index
     * columns, and a uniqueness of its own over them in the shape the database needs ({@link SharedJoinTableUniqueness}):
     * a position, or a map key, is taken once in the collection, while its elements may repeat.
     */
    private void makeIndexedCollectionsShareable(List<Collection> sharing, Table table) {
        for (Collection member : sharing) {
            if (!(member instanceof IndexedCollection indexed) || indexed.getIndex() == null) {
                continue;
            }
            List<Column> keyColumns = member.getKey().getColumns();
            List<Column> indexColumns = new ArrayList<>();
            for (Selectable selectable : indexed.getIndex().getSelectables()) {
                if (selectable instanceof Column column) {
                    indexColumns.add(column);
                }
            }
            if (keyColumns.isEmpty() || indexColumns.isEmpty()) {
                continue;
            }
            Set<Column> own = new HashSet<>(keyColumns);
            own.addAll(indexColumns);
            if (table.hasPrimaryKey() && own.containsAll(table.getPrimaryKey().getColumns())) {
                table.setPrimaryKey(null);
            }
            keyColumns.forEach(column -> column.setNullable(true));
            indexColumns.forEach(column -> column.setNullable(true));
            uniqueness.apply(table, keyColumns, indexColumns, new ImplicitNaming(table));
        }
    }

    private static boolean sharesKeyColumnWithOther(Collection collection, List<Collection> sharing) {
        if (collection.getKey() == null) {
            return false;
        }
        Set<Column> ownKeyColumns = new HashSet<>(collection.getKey().getColumns());
        for (Collection other : sharing) {
            if (other != collection && other.getKey() != null) {
                for (Column otherKeyCol : other.getKey().getColumns()) {
                    if (ownKeyColumns.contains(otherKeyCol)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether the table holds a column that the collection does not use: its key, element, index and identifier
     * columns are its own, and a column besides those belongs to another collection that shares the table. A table
     * only this collection uses is not searched for the others, which keeps the binding of an application with many
     * collections linear. The collection of a shared table that is bound first finds none, and the one bound after
     * it restricts both.
     */
    private static boolean hasColumnsOfOthers(Collection collection, Table table) {
        Set<Column> own = new HashSet<>(collection.getKey().getColumns());
        own.addAll(columnsOf(collection));
        if (collection instanceof IndexedCollection indexed && indexed.getIndex() != null) {
            addColumns(own, indexed.getIndex());
        }
        if (collection instanceof IdentifierCollection idbag && idbag.getIdentifier() != null) {
            addColumns(own, idbag.getIdentifier());
        }
        for (Column column : table.getColumns()) {
            if (!own.contains(column)) {
                return true;
            }
        }
        return false;
    }

    private static void addColumns(Set<Column> columns, Value value) {
        for (Selectable selectable : value.getSelectables()) {
            if (selectable instanceof Column column) {
                columns.add(column);
            }
        }
    }

    /**
     * Whether an element column is a large object, which a database cannot index. Hibernate leaves the set of such an
     * element without a key ({@code Set.createPrimaryKey}), and so does this binder.
     */
    static boolean hasLobColumn(Metadata metadata, List<Column> elementColumns) {
        for (Column column : elementColumns) {
            try {
                if (column.isSqlTypeLob(metadata)) {
                    return true;
                }
            }
            catch (MappingException ignored) {
                // the type of the column cannot be resolved, as Hibernate decides when it makes the same check
            }
        }
        return false;
    }

    private static List<Column> columnsOf(Collection collection) {
        List<Column> columns = new ArrayList<>();
        if (collection.getElement() != null) {
            for (Selectable selectable : collection.getElement().getSelectables()) {
                if (selectable instanceof Column column) {
                    columns.add(column);
                }
            }
        }
        return columns;
    }

    /** Names constraints and indexes with the implicit naming strategy, as Hibernate names the ones it creates. */
    private final class ImplicitNaming implements SharedJoinTableUniqueness.Naming {

        private final Table table;

        private ImplicitNaming(Table table) {
            this.table = table;
        }

        @Override
        public String uniqueKeyName(List<Column> columns) {
            return strategy().determineUniqueKeyName(new ImplicitUniqueKeyNameSource() {
                @Override
                public Identifier getTableName() {
                    return table.getNameIdentifier();
                }

                @Override
                public List<Identifier> getColumnNames() {
                    return identifiers(columns);
                }

                @Override
                public Identifier getUserProvidedIdentifier() {
                    return null;
                }

                @Override
                public MetadataBuildingContext getBuildingContext() {
                    return metadataBuildingContext;
                }
            }).render(dialect());
        }

        @Override
        public String indexName(List<Column> columns) {
            return strategy().determineIndexName(new ImplicitIndexNameSource() {
                @Override
                public Identifier getTableName() {
                    return table.getNameIdentifier();
                }

                @Override
                public List<Identifier> getColumnNames() {
                    return identifiers(columns);
                }

                @Override
                public Identifier getUserProvidedIdentifier() {
                    return null;
                }

                @Override
                public MetadataBuildingContext getBuildingContext() {
                    return metadataBuildingContext;
                }
            }).render(dialect());
        }

        private List<Identifier> identifiers(List<Column> columns) {
            List<Identifier> names = new ArrayList<>();
            for (Column column : columns) {
                names.add(column.getNameIdentifier(metadataBuildingContext));
            }
            return names;
        }

        private ImplicitNamingStrategy strategy() {
            return metadataBuildingContext.getBuildingOptions().getImplicitNamingStrategy();
        }

        private Dialect dialect() {
            return metadataBuildingContext.getMetadataCollector().getDatabase().getDialect();
        }
    }
}

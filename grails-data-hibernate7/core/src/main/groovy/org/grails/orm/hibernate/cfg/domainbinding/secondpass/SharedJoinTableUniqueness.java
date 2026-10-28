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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;

import org.grails.orm.hibernate.dialect.UniqueIndexNulls;

/**
 * How a join table that several collections of one owner share keeps the rows of each collection unique, by
 * database.
 *
 * <p>Such a table has a key column per collection and one element column, or one key column and an element column per
 * collection, and every row leaves the columns of the other collections null. A row is unique by the key column of its own collection and the element, so each
 * collection needs a constraint of its own over those two; one constraint over all key columns would hold a null in
 * every row and reject nothing on a database that treats nulls as distinct. What that per-collection constraint has
 * to look like depends on how the unique index of the database treats a null in its key
 * ({@link UniqueIndexNulls}):</p>
 * <ul>
 * <li>{@link #KEY_PER_COLLECTION}, for {@link UniqueIndexNulls#DISTINCT}: a unique key over the key column and the
 * element. The rows of the other collections, null in this key column, never collide.</li>
 * <li>{@link #ONE_KEY_FOR_ALL_COLLECTIONS}, for {@link UniqueIndexNulls#COMPARED}: Oracle compares the non-null columns of a partially null row, so a unique key per
 * collection would reject the rows of the other collections, null in that collection's columns and equal in the ones
 * they have in common. One unique key over the key columns of every collection and the element columns is right
 * there: a row collides only with a row that is non-null in the same columns, that is a row of its own collection,
 * for the same element. That holds for collections that share the element column and have a key column each, and
 * for collections that share the key column and have an element column each. Hibernate renders that key as a plain
 * constraint on Oracle, where it is what is wanted; on a database that treats nulls as distinct the same key would
 * hold a null in every row and reject nothing, which is why it is for {@code COMPARED} alone.</li>
 * </ul>
 *
 * @since 8.0.1
 */
public enum SharedJoinTableUniqueness {

    /** Where nulls are compared: one unique key over the key columns of every collection sharing the table and the element. */
    ONE_KEY_FOR_ALL_COLLECTIONS(UniqueIndexNulls.COMPARED) {
        @Override
        void apply(Table table, List<Column> keyColumns, List<Column> elementColumns, Naming naming) {
            List<Column> own = concat(keyColumns, elementColumns);
            if (coveredByPrimaryKey(table, own)) {
                return;
            }
            // the unique key Hibernate created for the first collection sharing this table takes the columns of every
            // later one that shares a column with it, the element column or the key column: Oracle compares the
            // non-null columns of a row, so a row collides only with a row of its own collection for the same element,
            // and a plain key per collection would reject the rows of the other collections, null in that collection's
            // columns and equal in the ones they have in common
            for (UniqueKey uniqueKey : table.getUniqueKeys().values()) {
                if (uniqueKey.isExplicit() || own.stream().noneMatch(uniqueKey::containsColumn)) {
                    continue;
                }
                for (Column column : own) {
                    if (!uniqueKey.containsColumn(column)) {
                        uniqueKey.addColumn(column);
                    }
                }
            }
        }
    },

    /** Where nulls are distinct: a unique key per collection over its key column and the element. */
    KEY_PER_COLLECTION(UniqueIndexNulls.DISTINCT) {
        @Override
        void apply(Table table, List<Column> keyColumns, List<Column> elementColumns, Naming naming) {
            List<Column> own = concat(keyColumns, elementColumns);
            if (coveredByPrimaryKey(table, own)) {
                return;
            }
            Set<Column> wanted = new HashSet<>(own);
            boolean present = table.getUniqueKeys().values().stream()
                    .anyMatch(uniqueKey -> new HashSet<>(uniqueKey.getColumns()).equals(wanted));
            if (present) {
                return;
            }
            UniqueKey uniqueKey = new UniqueKey(table);
            uniqueKey.setName(naming.uniqueKeyName(own));
            own.forEach(uniqueKey::addColumn);
            table.addUniqueKey(uniqueKey);
        }
    };

    /** Names a constraint or index the way the implicit naming strategy of the metadata would. */
    public interface Naming {

        /** The name of an implicit unique key over the columns. */
        String uniqueKeyName(List<Column> columns);

        /** The name of an implicit index over the columns. */
        String indexName(List<Column> columns);
    }

    private final UniqueIndexNulls indexNulls;

    SharedJoinTableUniqueness(UniqueIndexNulls indexNulls) {
        this.indexNulls = indexNulls;
    }

    /** The uniqueness that fits how the unique index of the dialect's database treats a null in its key. */
    public static SharedJoinTableUniqueness forDialect(Dialect dialect) {
        UniqueIndexNulls nulls = UniqueIndexNulls.forDialect(dialect);
        return Arrays.stream(values()).filter(uniqueness -> uniqueness.indexNulls == nulls).findFirst().orElseThrow();
    }

    /**
     * Gives the collection whose key and element columns these are a constraint of its own on the table, unless the
     * table has one already.
     */
    abstract void apply(Table table, List<Column> keyColumns, List<Column> elementColumns, Naming naming);

    private static boolean coveredByPrimaryKey(Table table, List<Column> columns) {
        return table.hasPrimaryKey() && new HashSet<>(table.getPrimaryKey().getColumns()).containsAll(columns);
    }

    private static List<Column> concat(List<Column> first, List<Column> second) {
        List<Column> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}

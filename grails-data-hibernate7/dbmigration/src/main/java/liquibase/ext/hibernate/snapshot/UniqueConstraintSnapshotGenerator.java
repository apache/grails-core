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
package liquibase.ext.hibernate.snapshot;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Random;

import liquibase.Scope;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.Index;
import liquibase.structure.core.Table;
import liquibase.structure.core.UniqueConstraint;
import org.hibernate.HibernateException;

public class UniqueConstraintSnapshotGenerator extends HibernateSnapshotGenerator {

    private static final int MAX_NAME_LENGTH = 64;
    private static final int SHORTENED_NAME_LENGTH = 63;
    private static final int START_INDEX = 0;
    private static final int FIRST_COLUMN = 0;
    private static final String SEARCH_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Random RANDOM = new Random();

    public UniqueConstraintSnapshotGenerator() {
        super(UniqueConstraint.class, Table.class);
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        return example;
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(UniqueConstraint.class)) {
            return;
        }

        if (foundObject instanceof Table table) {
            var hibernateTable = findHibernateTable(table, snapshot);
            if (hibernateTable == null) {
                return;
            }
            for (var hibernateUnique : hibernateTable.getUniqueKeys().values()) {
                var uniqueConstraint = new UniqueConstraint();
                uniqueConstraint.setName(hibernateUnique.getName());
                uniqueConstraint.setRelation(table);
                uniqueConstraint.setClustered(Boolean.FALSE); // No way to set true via Hibernate

                int i = 0;
                for (var hibernateColumn : hibernateUnique.getColumns()) {
                    uniqueConstraint.addColumn(i++, new Column(hibernateColumn.getName()).setRelation(table));
                }

                Index index = getBackingIndex(uniqueConstraint, hibernateTable, snapshot);
                uniqueConstraint.setBackingIndex(index);

                Scope.getCurrentScope().getLog(getClass()).info("Found unique constraint " + uniqueConstraint);
                table.getUniqueConstraints().add(uniqueConstraint);
            }
            for (var column : hibernateTable.getColumns()) {
                if (column.isUnique()) {
                    UniqueConstraint uniqueConstraint = new UniqueConstraint();
                    uniqueConstraint.setRelation(table);
                    uniqueConstraint.setClustered(Boolean.FALSE); // No way to set true via Hibernate
                    String name = "UC_" + table.getName().toUpperCase(Locale.ROOT) +
                            column.getName().toUpperCase(Locale.ROOT) + "_COL";
                    if (name.length() > MAX_NAME_LENGTH) {
                        name = name.substring(START_INDEX, SHORTENED_NAME_LENGTH);
                    }
                    uniqueConstraint.addColumn(FIRST_COLUMN, new Column(column.getName()).setRelation(table));
                    uniqueConstraint.setName(name);
                    Scope.getCurrentScope().getLog(getClass()).info("Found unique constraint " + uniqueConstraint);
                    table.getUniqueConstraints().add(uniqueConstraint);

                    Index index = getBackingIndex(uniqueConstraint, hibernateTable, snapshot);
                    uniqueConstraint.setBackingIndex(index);
                }
            }

            for (UniqueConstraint uc : table.getUniqueConstraints()) {
                if (uc.getName() == null || uc.getName().isEmpty()) {
                    String name = table.getName() + uc.getColumnNames();
                    name = "UCIDX" + hashedName(name);
                    uc.setName(name);
                }
            }
        }
    }

    private String hashedName(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.reset();
            md.update(s.getBytes());
            byte[] digest = md.digest();
            BigInteger bigInt = new BigInteger(1, digest);
            // By converting to base 35 (full alphanumeric), we guarantee
            // that the length of the name will always be smaller than the 30
            // character identifier restriction enforced by a few dialects.
            return bigInt.toString(35);
        } catch (NoSuchAlgorithmException e) {
            throw new HibernateException("Unable to generate a hashed name!", e);
        }
    }

    protected Index getBackingIndex(
            UniqueConstraint uniqueConstraint, org.hibernate.mapping.Table hibernateTable, DatabaseSnapshot snapshot) {
        Index index = new Index();
        index.setRelation(uniqueConstraint.getRelation());
        index.setColumns(uniqueConstraint.getColumns());
        index.setUnique(true);
        index.setName(String.format("%s_%s_IX", hibernateTable.getName(), randomIdentifier(4)));

        return index;
    }

    private String randomIdentifier(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(SEARCH_CHARS.charAt(RANDOM.nextInt(SEARCH_CHARS.length())));
        }
        return sb.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {liquibase.snapshot.jvm.UniqueConstraintSnapshotGenerator.class};
    }
}

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

import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.ForeignKey;
import liquibase.structure.core.Table;
import org.hibernate.mapping.Column;

public class HibernateForeignKeySnapshotGenerator extends HibernateSnapshotGenerator {

    public HibernateForeignKeySnapshotGenerator() {
        super(ForeignKey.class, new Class[] {Table.class});
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        return example;
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(ForeignKey.class)) {
            return;
        }
        if (foundObject instanceof Table table) {
            org.hibernate.mapping.Table hibernateTable = findHibernateTable(table, snapshot);
            if (hibernateTable == null) {
                return;
            }

            for (org.hibernate.mapping.ForeignKey hibernateForeignKey : hibernateTable.getForeignKeyCollection()) {
                if (hibernateForeignKey.isCreationEnabled()) {
                    org.hibernate.mapping.Table hibernateReferencedTable = hibernateForeignKey.getReferencedTable();

                    Table referencedTable = new Table().setName(hibernateReferencedTable.getName());
                    referencedTable.setSchema(
                            hibernateReferencedTable.getCatalog(), hibernateReferencedTable.getSchema());

                    ForeignKey fk = new ForeignKey();
                    fk.setName(hibernateForeignKey.getName());
                    fk.setPrimaryKeyTable(referencedTable);
                    fk.setForeignKeyTable(table);
                    for (Column column : hibernateForeignKey.getColumns()) {
                        fk.addForeignKeyColumn(new liquibase.structure.core.Column(column.getName()));
                    }
                    for (Column column : hibernateForeignKey.getReferencedColumns()) {
                        fk.addPrimaryKeyColumn(new liquibase.structure.core.Column(column.getName()));
                    }
                    if (fk.getPrimaryKeyColumns() == null ||
                            fk.getPrimaryKeyColumns().isEmpty()) {
                        if (hibernateReferencedTable.getPrimaryKey() != null) {
                            for (Column column :
                                    hibernateReferencedTable.getPrimaryKey().getColumns()) {
                                fk.addPrimaryKeyColumn(new liquibase.structure.core.Column(column.getName()));
                            }
                        }
                    }

                    fk.setDeferrable(false);
                    fk.setInitiallyDeferred(false);

                    table.getOutgoingForeignKeys().add(fk);
                    if (table.getSchema() != null) {
                        table.getSchema().addDatabaseObject(fk);
                    }
                }
            }
        }
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {liquibase.snapshot.jvm.ForeignKeySnapshotGenerator.class};
    }
}

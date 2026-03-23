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
package liquibase.ext.hibernate.snapshot.extension;

import liquibase.structure.core.Column;
import liquibase.structure.core.DataType;
import liquibase.structure.core.PrimaryKey;
import liquibase.structure.core.Table;
import org.hibernate.generator.Generator;
import org.hibernate.id.enhanced.TableGenerator;

public class TableGeneratorSnapshotGenerator implements ExtendedSnapshotGenerator<Generator, Table> {

    private static final String PK_DATA_TYPE = "varchar";
    private static final String VALUE_DATA_TYPE = "bigint";

    @Override
    public Table snapshot(Generator ig) {
        TableGenerator tableGenerator = (TableGenerator) ig;
        Table table = new Table().setName(tableGenerator.getTableName());

        Column pkColumn = new Column();
        pkColumn.setName(tableGenerator.getSegmentColumnName());
        DataType pkDataType = new DataType(PK_DATA_TYPE);
        pkDataType.setColumnSize(tableGenerator.getSegmentValueLength());
        pkColumn.setType(pkDataType);
        pkColumn.setCertainDataType(false);
        pkColumn.setRelation(table);
        table.getColumns().add(pkColumn);

        PrimaryKey primaryKey = new PrimaryKey();
        primaryKey.setName(tableGenerator.getTableName() + "PK");
        primaryKey.addColumn(0, new Column(pkColumn.getName()).setRelation(table));
        primaryKey.setTable(table);
        table.setPrimaryKey(primaryKey);

        Column valueColumn = new Column();
        valueColumn.setName(tableGenerator.getValueColumnName());
        valueColumn.setType(new DataType(VALUE_DATA_TYPE));
        valueColumn.setNullable(false);
        valueColumn.setCertainDataType(false);
        valueColumn.setRelation(table);
        table.getColumns().add(valueColumn);

        return table;
    }

    @Override
    public boolean supports(Generator ig) {
        return ig instanceof TableGenerator;
    }
}

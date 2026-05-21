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

databaseChangeLog = {

    changeSet(author: "John Smith", id: "1") {
        createTable(tableName: "author") {
            column(autoIncrement: "true", name: "id", type: "BIGINT") {
                constraints(primaryKey: "true", primaryKeyName: "authorPK")
            }

            column(name: "version", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "name", type: "VARCHAR(255)") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "John Smith", id: "2") {
        createTable(tableName: "book") {
            column(autoIncrement: "true", name: "id", type: "BIGINT") {
                constraints(primaryKey: "true", primaryKeyName: "bookPK")
            }

            column(name: "version", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "author_id", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "title", type: "VARCHAR(255)") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "John Smith", id: "3") {
        addForeignKeyConstraint(baseColumnNames: "author_id", baseTableName: "book", constraintName: "FK_4sac2ubmnqva85r8bk8fxdvbf", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "author")
    }

    changeSet(author: "John Smith", id: "4", context: "development") {
        insert(tableName: "author") {
            column(name: "name", value: "Mary")
            column(name: "version", value: "0")
        }
    }

    changeSet(author: "John Smith", id: "5", context: "test") {
        insert(tableName: "author") {
            column(name: "name", value: "Amelia")
            column(name: "version", value: "0")
        }
    }
}

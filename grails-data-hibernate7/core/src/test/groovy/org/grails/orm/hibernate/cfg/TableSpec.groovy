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
package org.grails.orm.hibernate.cfg

import spock.lang.Specification

class TableSpec extends Specification {

    def "configureNew with closure sets all fields"() {
        when:
        Table table = Table.configureNew {
            name 'my_table'
            catalog 'my_catalog'
            schema 'my_schema'
        }

        then:
        table.name == 'my_table'
        table.catalog == 'my_catalog'
        table.schema == 'my_schema'
    }

    def "configureNew with closure setting only name leaves catalog and schema null"() {
        when:
        Table table = Table.configureNew {
            name 'orders'
        }

        then:
        table.name == 'orders'
        table.catalog == null
        table.schema == null
    }

    def "configureExisting with map updates only provided fields"() {
        given:
        Table table = new Table(name: 'original', catalog: 'cat', schema: 'sch')

        when:
        Table result = Table.configureExisting(table, [name: 'updated'])

        then:
        result.is(table)
        result.name == 'updated'
        result.catalog == 'cat'
        result.schema == 'sch'
    }

    def "configureExisting with map sets multiple fields at once"() {
        given:
        Table table = new Table()

        when:
        Table result = Table.configureExisting(table, [name: 'orders', catalog: 'shop', schema: 'public'])

        then:
        result.is(table)
        result.name == 'orders'
        result.catalog == 'shop'
        result.schema == 'public'
    }

    def "configureExisting with empty map leaves fields unchanged"() {
        given:
        Table table = new Table(name: 'products', catalog: 'store', schema: 'dbo')

        when:
        Table result = Table.configureExisting(table, [:])

        then:
        result.is(table)
        result.name == 'products'
        result.catalog == 'store'
        result.schema == 'dbo'
    }

    def "configureExisting with closure updates an existing table"() {
        given:
        Table table = new Table(name: 'old_name')

        when:
        Table result = Table.configureExisting(table) {
            name 'new_name'
            schema 'public'
        }

        then:
        result.is(table)
        result.name == 'new_name'
        result.schema == 'public'
    }

    def "builder-style setters return the table instance for chaining"() {
        when:
        Table table = new Table().name('items').catalog('shop').schema('dbo')

        then:
        table.name == 'items'
        table.catalog == 'shop'
        table.schema == 'dbo'
    }

    def "default constructor produces a table with all fields null"() {
        when:
        Table table = new Table()

        then:
        table.name == null
        table.catalog == null
        table.schema == null
    }

    // ── JoinTable ──────────────────────────────────────────────────────────────

    def "JoinTable extends Table and inherits name/schema fields"() {
        when:
        JoinTable jt = new JoinTable(name: 'join_table', schema: 'public')

        then:
        jt.name == 'join_table'
        jt.schema == 'public'
        jt.key == null
        jt.column == null
    }

    def "JoinTable key(String) sets key column name and returns this"() {
        given:
        JoinTable jt = new JoinTable()

        when:
        def result = jt.key('owner_id')

        then:
        result.is(jt)
        jt.key.name == 'owner_id'
    }

    def "JoinTable column(String) sets column name and returns this"() {
        given:
        JoinTable jt = new JoinTable()

        when:
        def result = jt.column('item_id')

        then:
        result.is(jt)
        jt.column.name == 'item_id'
    }

    def "JoinTable key(Closure) configures a ColumnConfig"() {
        given:
        JoinTable jt = new JoinTable()

        when:
        jt.key { name 'fk_id'; length 20 }

        then:
        jt.key.name == 'fk_id'
        jt.key.length == 20
    }

    def "JoinTable column(Closure) configures a ColumnConfig"() {
        given:
        JoinTable jt = new JoinTable()

        when:
        jt.column { name 'child_id'; sqlType 'bigint' }

        then:
        jt.column.name == 'child_id'
        jt.column.sqlType == 'bigint'
    }
}

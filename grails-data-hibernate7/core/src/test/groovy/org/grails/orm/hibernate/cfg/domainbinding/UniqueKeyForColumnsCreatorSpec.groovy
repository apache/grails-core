/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.mapping.Column
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey
import spock.lang.Specification

import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueKeyForColumnsCreator
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueNameGenerator

class UniqueKeyForColumnsCreatorSpec extends Specification {

    def 'Test that createUniqueKeyForColumns adds a unique key to the table'() {
        given:
        def generator = new UniqueNameGenerator()
        def table = new Table('test', 'my_table')
        def creator = new UniqueKeyForColumnsCreator(generator)
        def columns = [new Column('col1'), new Column('col2')]

        when:
        creator.createUniqueKeyForColumns(table, columns)

        then:
        def keys = table.getUniqueKeys().values().toList()
        keys.size() == 1
        UniqueKey uk = keys[0]
        uk.table == table
        uk.columns.size() == 2
        // The creator reverses the list
        uk.columns.get(0).name == 'col2'
        uk.columns.get(1).name == 'col1'
        uk.getName() != null
    }

    def 'default constructor creates a functional UniqueKeyForColumnsCreator'() {
        given:
        def creator = new UniqueKeyForColumnsCreator()
        def table = new Table('test', 'my_table_2')
        def columns = [new Column('a'), new Column('b')]

        when:
        creator.createUniqueKeyForColumns(table, columns)

        then:
        def keys = table.getUniqueKeys().values().toList()
        keys.size() == 1
        keys[0].columns*.name.toSet() == ['a', 'b'].toSet()
    }

    def 'createUniqueKeyForColumns works with empty columns list'() {
        given:
        def creator = new UniqueKeyForColumnsCreator()
        def table = new Table('test', 'empty_table')
        def columns = []

        when:
        creator.createUniqueKeyForColumns(table, columns)

        then:
        def keys = table.getUniqueKeys().values().toList()
        keys.size() == 1
        keys[0].columns.size() == 0
    }
}

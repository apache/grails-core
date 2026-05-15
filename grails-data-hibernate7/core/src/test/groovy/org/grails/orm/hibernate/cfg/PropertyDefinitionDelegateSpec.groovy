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
package org.grails.orm.hibernate.cfg

import spock.lang.Specification

class PropertyDefinitionDelegateSpec extends Specification {

    def 'test column method with multiple columns'() {
        given:
        def config = new PropertyConfig()
        def delegate = new PropertyDefinitionDelegate(config)

        when:
        delegate.column(name: 'col1', sqlType: 'varchar(255)')
        delegate.column(name: 'col2', sqlType: 'integer')

        then:
        config.columns.size() == 2
        config.columns[0].name == 'col1'
        config.columns[0].sqlType == 'varchar(255)'
        config.columns[1].name == 'col2'
        config.columns[1].sqlType == 'integer'
    }

    def 'test re-evaluation of column method with multiple columns'() {
        given:
        def config = new PropertyConfig()
        def delegate1 = new PropertyDefinitionDelegate(config)
        delegate1.column(name: 'col1', sqlType: 'varchar(255)')
        delegate1.column(name: 'col2', sqlType: 'integer')

        when: 're-evaluating with a new delegate instance but same config'
        def delegate2 = new PropertyDefinitionDelegate(config)
        delegate2.column(name: 'new_col1', sqlType: 'text')
        delegate2.column(name: 'new_col2', sqlType: 'long')

        then:
        config.columns.size() == 2
        config.columns[0].name == 'new_col1'
        config.columns[0].sqlType == 'text'
        config.columns[1].name == 'new_col2'
        config.columns[1].sqlType == 'long'
    }

    def 'column without name throws DatastoreConfigurationException'() {
        given:
        def config = new PropertyConfig()
        def delegate = new PropertyDefinitionDelegate(config)

        when:
        delegate.column(sqlType: 'varchar(255)')

        then:
        thrown(org.grails.datastore.mapping.model.DatastoreConfigurationException)
    }

    def 'column with all optional attributes sets them correctly'() {
        given:
        def config = new PropertyConfig()
        def delegate = new PropertyDefinitionDelegate(config)

        when:
        def col = delegate.column(
            name: 'amount',
            sqlType: 'decimal',
            enumType: 'ordinal',
            index: 'idx_amount',
            unique: true,
            length: 10,
            precision: 5,
            scale: 2
        )

        then:
        col.name == 'amount'
        col.sqlType == 'decimal'
        col.enumType == 'ordinal'
        col.index == 'idx_amount'
        col.unique == true
        col.length == 10
        col.precision == 5
        col.scale == 2
    }

    def 'column with minimal args uses defaults for optional fields'() {
        given:
        def config = new PropertyConfig()
        def delegate = new PropertyDefinitionDelegate(config)

        when:
        def col = delegate.column(name: 'simple')

        then:
        col.name == 'simple'
        col.sqlType == null
        col.unique == false
        col.length == -1
        col.precision == -1
        col.scale == -1
    }
}

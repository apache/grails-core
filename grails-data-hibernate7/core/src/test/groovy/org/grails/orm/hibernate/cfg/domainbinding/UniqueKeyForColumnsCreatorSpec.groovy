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

package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.mapping.Column
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey
import spock.lang.Specification

import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueKeyForColumnsCreator
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueNameGenerator

class UniqueKeyForColumnsCreatorSpec extends Specification {

    def "Test that createUniqueKeyForColumns adds a unique key to the table"() {
        given:
        UniqueNameGenerator mockUniqueNameGenerator = Mock()
        Table mockTable = Mock()
        def creator = new UniqueKeyForColumnsCreator(mockUniqueNameGenerator)
        def columns = [new Column("col1"), new Column("col2")]

        when:
        creator.createUniqueKeyForColumns(mockTable, columns)

        then:
        1 * mockTable.addUniqueKey({ UniqueKey uk ->
            uk.table == mockTable
            uk.columns.size() == 2
            // The creator reverses the list
            uk.columns.get(0).name == "col2"
            uk.columns.get(1).name == "col1"
        })
        1 * mockUniqueNameGenerator.setGeneratedUniqueName({ UniqueKey uk ->
            uk.table == mockTable
            uk.columns.size() == 2
        })
    }
}

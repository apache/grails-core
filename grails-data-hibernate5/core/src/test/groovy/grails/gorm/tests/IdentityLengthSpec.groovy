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

package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.mapping.MappingBuilder
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue
import spock.lang.Unroll

@Issue('https://github.com/apache/grails-core/issues/14497')
class IdentityLengthSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([BuilderIdLength, ClosureIdLength, BuilderIdLengthWildcard, ClosureIdLengthWildcard,
                                      IdLengthThenColumn, ColumnThenIdLength, WildcardIdLengthThenColumn, IdLengthThenSqlType])
    }

    private int idColumnLength(String table) {
        manager.hibernateSession.createSQLQuery(
                "select character_maximum_length from information_schema.columns where upper(table_name) = '${table}' and upper(column_name) = 'ID'"
        ).uniqueResult() as int
    }

    void 'the length given to the id in MappingBuilder.orm sizes the id column'() {
        expect:
        idColumnLength('BUILDER_ID_LENGTH') == 20
    }

    void 'the length given to the id in a mapping closure sizes the id column'() {
        expect:
        idColumnLength('CLOSURE_ID_LENGTH') == 20
    }

    private Map columnType(String table, String column) {
        List row = manager.hibernateSession.createSQLQuery(
                "select data_type, character_maximum_length from information_schema.columns where upper(table_name) = '${table}' and upper(column_name) = '${column}'"
        ).uniqueResult() as List
        [type: row[0], length: row[1] as int]
    }

    void 'a wildcard SQL type still reaches the id when MappingBuilder.orm gives the id a length'() {
        expect: 'the id takes the type every other column takes, as it does with the mapping closure'
        columnType('BUILDER_ID_LENGTH_WILDCARD', 'ID').type == columnType('BUILDER_ID_LENGTH_WILDCARD', 'NAME').type
        columnType('BUILDER_ID_LENGTH_WILDCARD', 'ID') == columnType('CLOSURE_ID_LENGTH_WILDCARD', 'ID')
    }

    private List<String> columnNames(String table) {
        manager.hibernateSession.createSQLQuery(
                "select column_name from information_schema.columns where upper(table_name) = '${table}' order by column_name"
        ).list() as List<String>
    }

    @Unroll
    void 'the id of #table keeps one column, named custom_id and #lengthText, when the length and a later declaration both reach it'() {
        expect:
        columnNames(table) == ['CUSTOM_ID', 'NAME', 'VERSION']
        columnType(table, 'CUSTOM_ID').length == length

        where:
        table                          | length
        'ID_LENGTH_THEN_COLUMN'        | 20
        'COLUMN_THEN_ID_LENGTH'        | 20
        'WILDCARD_ID_LENGTH_THEN_COLUMN' | 30
        lengthText = "sized ${length}"
    }

    void 'a later declaration of the id that gives no name keeps the id column and its length'() {
        expect:
        columnNames('ID_LENGTH_THEN_SQL_TYPE') == ['ID', 'NAME', 'VERSION']
        columnType('ID_LENGTH_THEN_SQL_TYPE', 'ID').type == 'CHARACTER'
    }

    void 'a wildcard SQL type still reaches the id when the mapping closure gives the id a length'() {
        expect:
        columnType('CLOSURE_ID_LENGTH_WILDCARD', 'ID').type == columnType('CLOSURE_ID_LENGTH_WILDCARD', 'NAME').type
    }
}

@Entity
class BuilderIdLength {
    String id

    static mapping = MappingBuilder.orm {
        id generator: 'assigned', length: 20
    }
}

@Entity
class ClosureIdLength {
    String id

    static mapping = {
        id generator: 'assigned', length: 20
    }
}

@Entity
class BuilderIdLengthWildcard {
    String id
    String name

    static mapping = MappingBuilder.orm {
        '*'(property {
            column {
                sqlType 'char(30)'
            }
        })
        id generator: 'assigned', length: 20
    }
}

@Entity
class ClosureIdLengthWildcard {
    String id
    String name

    static mapping = {
        '*'(sqlType: 'char(30)')
        id generator: 'assigned', length: 20
    }
}

@Entity
class IdLengthThenColumn {
    String id
    String name

    static mapping = MappingBuilder.orm {
        id generator: 'assigned', length: 20
        property('id') {
            column {
                name 'custom_id'
            }
        }
    }
}

@Entity
class ColumnThenIdLength {
    String id
    String name

    static mapping = MappingBuilder.orm {
        property('id') {
            column {
                name 'custom_id'
            }
        }
        id generator: 'assigned', length: 20
    }
}

@Entity
class WildcardIdLengthThenColumn {
    String id
    String name

    static mapping = MappingBuilder.orm {
        '*'(property {
            column {
                sqlType 'char(30)'
            }
        })
        id generator: 'assigned', length: 20
        property('id') {
            column {
                name 'custom_id'
            }
        }
    }
}

@Entity
class IdLengthThenSqlType {
    String id
    String name

    static mapping = MappingBuilder.orm {
        id generator: 'assigned', length: 20
        property('id') {
            column {
                sqlType 'char(20)'
            }
        }
    }
}

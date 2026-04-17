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
import spock.lang.Unroll

class ColumnConfigSpec extends Specification {

    void "test default values"() {
        when:
        def config = new ColumnConfig()

        then:
        config.enumType == 'default'
        config.unique == false
        config.length == -1
        config.precision == -1
        config.scale == -1
    }

    void "test configureNew with closure"() {
        when:
        def config = ColumnConfig.configureNew {
            name "my_column"
            sqlType "varchar(255)"
            index "my_index"
            unique true
            length 100
            precision 10
            scale 2
            defaultValue "default_val"
            comment "my comment"
            read "read_sql"
            write "write_sql"
        }

        then:
        config.name == "my_column"
        config.sqlType == "varchar(255)"
        config.index == "my_index"
        config.unique == true
        config.length == 100
        config.precision == 10
        config.scale == 2
        config.defaultValue == "default_val"
        config.comment == "my comment"
        config.read == "read_sql"
        config.write == "write_sql"
    }

    void "test configureNew with map"() {
        when:
        def config = ColumnConfig.configureNew(
            name: "my_column",
            sqlType: "varchar(255)",
            index: "my_index",
            unique: true,
            length: 100,
            precision: 10,
            scale: 2,
            defaultValue: "default_val",
            comment: "my comment",
            read: "read_sql",
            write: "write_sql"
        )

        then:
        config.name == "my_column"
        config.sqlType == "varchar(255)"
        config.index == "my_index"
        config.unique == true
        config.length == 100
        config.precision == 10
        config.scale == 2
        config.defaultValue == "default_val"
        config.comment == "my comment"
        config.read == "read_sql"
        config.write == "write_sql"
    }

    @Unroll
    void "test getIndexAsMap with valid input: #input"() {
        given:
        def config = new ColumnConfig(index: input)

        expect:
        config.getIndexAsMap() == expected

        where:
        input                                      | expected
        null                                       | [:]
        [:]                                        | [:]
        [column: 'foo', type: 'string']            | [column: 'foo', type: 'string']
        "my_idx"                                   | [column: "my_idx"]
        "invalid_format"                           | [column: "invalid_format"]
        "[]"                                       | [:]
        "  "                                       | [:]
        "column:item_idx, type:integer"            | [column: "item_idx", type: "integer"]
        "[column:item_idx, type:integer]"          | [column: "item_idx", type: "integer"]
        "column:'item_idx', type:'integer'"        | [column: "item_idx", type: "integer"]
        'column:"item_idx", type:"integer"'        | [column: "item_idx", type: "integer"]
        "  column : item_idx ,  type : integer  "  | [column: "item_idx", type: "integer"]
    }

    @Unroll
    void "test getIndexAsMap with invalid input: #input"() {
        given:
        def config = new ColumnConfig(index: input)

        when:
        config.getIndexAsMap()

        then:
        thrown(IllegalArgumentException)

        where:
        input << [
            "column:foo, invalid",
            "column:foo, invalid:bar, extra"
        ]
    }

    void "test getIndexAsMap with non-string non-map input returns empty map"() {
        given:
        def config = new ColumnConfig(index: { "closure" })

        expect:
        config.getIndexAsMap() == [:]
    }

    void "test toString"() {
        given:
        def config = new ColumnConfig(name: "foo", index: "bar", unique: true, length: 10, precision: 5, scale: 2)

        expect:
        config.toString() == "column[name:foo, index:bar, unique:true, length:10, precision:5, scale:2]"
    }

    void "test isUnique with various values"() {
        expect:
        new ColumnConfig(unique: true).isUnique() == true
        new ColumnConfig(unique: false).isUnique() == false
        new ColumnConfig(unique: "true").isUnique() == true
        new ColumnConfig(unique: "any string").isUnique() == true
        new ColumnConfig(unique: null).isUnique() == false
    }

    void "test clone"() {
        given:
        def config = new ColumnConfig(name: "foo", index: "bar", unique: true)

        when:
        def cloned = config.clone()

        then:
        cloned !== config
        cloned.name == config.name
        cloned.index == config.index
        cloned.unique == config.unique
    }

    void "test configureExisting with map"() {
        given:
        def config = new ColumnConfig(name: "old")

        when:
        ColumnConfig.configureExisting(config, [name: "new", length: 50])

        then:
        config.name == "new"
        config.length == 50
    }
}

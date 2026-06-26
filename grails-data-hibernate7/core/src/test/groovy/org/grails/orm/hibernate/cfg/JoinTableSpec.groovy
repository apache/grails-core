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

class JoinTableSpec extends Specification {

    def "should allow single key column config"() {
        given:
        def jt = new JoinTable(keys: [new ColumnConfig(name: 'a_col')])

        expect:
        jt.keys[0].name == 'a_col'
    }

    def "should allow child id column config"() {
        given:
        def jt = new JoinTable(column: new ColumnConfig(name: 'c'))

        expect:
        jt.column.name == 'c'
    }

    def "should support multiple key columns via keys field"() {
        given:
        def jt = new JoinTable(keys: [new ColumnConfig(name: 'a_col'), new ColumnConfig(name: 'b_col')])

        expect:
        jt.keys*.name == ['a_col', 'b_col']
    }

    def "keys is empty by default"() {
        expect:
        new JoinTable().keys.isEmpty()
    }
}

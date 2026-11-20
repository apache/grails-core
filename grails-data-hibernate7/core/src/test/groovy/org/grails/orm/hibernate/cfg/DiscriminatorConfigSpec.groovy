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

class DiscriminatorConfigSpec extends Specification {

    def "default constructor creates empty DiscriminatorConfig"() {
        when:
        def config = new DiscriminatorConfig()

        then:
        config.value == null
        config.column == null
        config.type == null
        config.insertable == null
        config.formula == null
    }

    def "value builder method sets discriminator value and returns this"() {
        given:
        def config = new DiscriminatorConfig()

        when:
        def result = config.value('TYPE_A')

        then:
        result.is(config)
        config.value == 'TYPE_A'
    }

    def "type builder method sets type and returns this"() {
        given:
        def config = new DiscriminatorConfig()

        when:
        def result = config.type('string')

        then:
        result.is(config)
        config.type == 'string'
    }

    def "formula builder method sets formula and returns this"() {
        given:
        def config = new DiscriminatorConfig()

        when:
        def result = config.formula('CASE WHEN dtype=1 THEN 1 ELSE 0 END')

        then:
        result.is(config)
        config.formula == 'CASE WHEN dtype=1 THEN 1 ELSE 0 END'
    }

    def "insertable builder method sets insertable and returns this"() {
        given:
        def config = new DiscriminatorConfig()

        when:
        def result = config.insertable(false)

        then:
        result.is(config)
        config.insertable == false
    }

    def "setInsert sets insertable field"() {
        given:
        def config = new DiscriminatorConfig()

        when:
        config.setInsert(true)

        then:
        config.insertable == true
    }

    def "column(Closure) configures column and returns this"() {
        given:
        def config = new DiscriminatorConfig()

        when:
        def result = config.column { name 'dtype'; sqlType 'varchar(10)' }

        then:
        result.is(config)
        config.column.name == 'dtype'
        config.column.sqlType == 'varchar(10)'
    }
}

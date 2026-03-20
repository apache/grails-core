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

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.mapping.Column
import spock.lang.Specification
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder

class NumericColumnConstraintsBinderSpec extends Specification {

    def binder = new NumericColumnConstraintsBinder()
    def column = new Column("test")

    def "should bind precision and scale when provided in column config"() {
        given:
        def cc = new ColumnConfig()
        cc.precision = 10
        cc.scale = 2

        when:
        binder.bindNumericColumnConstraints(column, cc, new PropertyConfig())

        then:
        column.precision == 10
        column.scale == 2
    }

    def "should calculate precision and scale from property config when not in column config"() {
        given:
        def cc = new ColumnConfig()
        def pc = new PropertyConfig()
        pc.scale = 4
        pc.min = -100
        pc.max = 1000

        when:
        binder.bindNumericColumnConstraints(column, cc, pc)

        then:
        column.precision == 8 // 4 digits + 4 scale
        column.scale == 4
    }

    def "should use default precision 15 for non-Oracle when no constraints"() {
        given:
        def nonOracleBinder = new NumericColumnConstraintsBinder(new org.hibernate.dialect.H2Dialect())
        def cc = new ColumnConfig()
        def pc = new PropertyConfig()

        when:
        nonOracleBinder.bindNumericColumnConstraints(column, cc, pc)

        then:
        column.precision == 15
    }

    def "should use default precision 126 for Oracle when no constraints"() {
        given:
        def oracleBinder = new NumericColumnConstraintsBinder(new org.hibernate.dialect.OracleDialect())
        def cc = new ColumnConfig()
        def pc = new PropertyConfig()

        when:
        oracleBinder.bindNumericColumnConstraints(column, cc, pc)

        then:
        column.precision == 126
    }
}

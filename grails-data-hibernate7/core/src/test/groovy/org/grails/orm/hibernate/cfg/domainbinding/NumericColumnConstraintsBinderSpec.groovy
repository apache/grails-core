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
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQLDialect
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
        binder.bindNumericColumnConstraints(column, cc, new PropertyConfig(), BigDecimal)

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
        binder.bindNumericColumnConstraints(column, cc, pc, BigDecimal)

        then:
        column.precision == 8 // 4 digits + 4 scale
        column.scale == 4
    }

    def "should default BigDecimal precision from the dialect's decimal default when no constraints"() {
        given:
        def h2Binder = new NumericColumnConstraintsBinder(new H2Dialect())
        def cc = new ColumnConfig()
        def pc = new PropertyConfig()

        when:
        h2Binder.bindNumericColumnConstraints(column, cc, pc, BigDecimal)

        then:
        column.precision == new H2Dialect().getDefaultDecimalPrecision()
    }

    def "should default decimal precision consistently across dialects without a per-dialect special case"() {
        given:
        def binderFor = new NumericColumnConstraintsBinder(dialect)
        def cc = new ColumnConfig()
        def pc = new PropertyConfig()
        def col = new Column("test")

        when:
        binderFor.bindNumericColumnConstraints(col, cc, pc, BigDecimal)

        then:
        col.precision == dialect.getDefaultDecimalPrecision()

        where:
        dialect << [new H2Dialect(), new PostgreSQLDialect(), new MySQLDialect(), new OracleDialect()]
    }

    def "should leave Float precision unset when no constraints, avoiding invalid float(n) DDL"() {
        given:
        def cc = new ColumnConfig()
        def pc = new PropertyConfig()

        when:
        binder.bindNumericColumnConstraints(column, cc, pc, Float)

        then:
        column.precision == null
    }

    def "should leave Double precision unset when no constraints, avoiding invalid float(n) DDL"() {
        given:
        def cc = new ColumnConfig()
        def pc = new PropertyConfig()

        when:
        binder.bindNumericColumnConstraints(column, cc, pc, Double)

        then:
        column.precision == null
    }

    def "should leave Float/Double precision unset even when min/max constraints are present"() {
        given:
        def cc = new ColumnConfig()
        def pc = new PropertyConfig()
        pc.min = -100
        pc.max = 1000

        when:
        binder.bindNumericColumnConstraints(column, cc, pc, propertyType)

        then: 'min/max only drives decimal precision - it must not leak into float/double precision'
        column.precision == null

        where:
        propertyType << [Float, Double]
    }

    def "should still honor an explicit column-config precision for Float/Double"() {
        given:
        def cc = new ColumnConfig()
        cc.precision = 10
        def pc = new PropertyConfig()

        when:
        binder.bindNumericColumnConstraints(column, cc, pc, propertyType)

        then:
        column.precision == 10

        where:
        propertyType << [Float, Double]
    }
}

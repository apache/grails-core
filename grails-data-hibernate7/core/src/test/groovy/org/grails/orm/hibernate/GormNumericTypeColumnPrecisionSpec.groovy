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
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.dialect.H2Dialect
import org.hibernate.mapping.PersistentClass

/**
 * Covers a regression where a Float/Double property with no explicit precision produced
 * invalid DDL (Hibernate rendered {@code float(64)}, which H2/PostgreSQL reject since their
 * FLOAT bit-precision ceiling is 53) that {@code hibernate.hbm2ddl.auto} logs but does not
 * throw for - the table is silently never created. FLOAT/DOUBLE precision is a *bit* count,
 * while NUMERIC/DECIMAL (BigDecimal) precision is a *decimal digit* count; applying one default
 * to every Number subtype conflates the two, and Hibernate itself converts a decimal-digit
 * precision into a bit count when rendering float(n) DDL - so NumericColumnConstraintsBinder now
 * leaves Float/Double precision unset (letting Hibernate fall back to the dialect's own correct
 * float/double DDL type directly) and only computes a decimal-digit default, from the dialect's
 * Dialect.getDefaultDecimalPrecision(), for BigDecimal/BigInteger. No per-dialect (e.g. Oracle)
 * special case is needed for either family.
 *
 * Runs against the default H2 datastore, so it needs no container - if schema creation in
 * setupSpec() silently drops the table, the live-DDL test below fails to find its columns. The
 * Testcontainers-backed multi-dialect DDL check lives separately in
 * {@link GormNumericTypeColumnIntegrationSpec}.
 */
class GormNumericTypeColumnPrecisionSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(NumericTypeMessage)
    }

    void "a Float property with no explicit precision is left unset for Hibernate's own dialect default"() {
        when:
        PersistentClass persistentClass = datastore.getMetadata().getEntityBinding(NumericTypeMessage.name)
        def column = persistentClass.getProperty('floatValue').getColumns().first()

        then:
        column.getPrecision() == null
    }

    void "a Double property with no explicit precision is left unset for Hibernate's own dialect default"() {
        when:
        PersistentClass persistentClass = datastore.getMetadata().getEntityBinding(NumericTypeMessage.name)
        def column = persistentClass.getProperty('doubleValue').getColumns().first()

        then:
        column.getPrecision() == null
    }

    void "a BigDecimal property with no explicit precision defaults to the dialect's decimal precision"() {
        when:
        PersistentClass persistentClass = datastore.getMetadata().getEntityBinding(NumericTypeMessage.name)
        def column = persistentClass.getProperty('bigDecimalValue').getColumns().first()

        then:
        column.getPrecision() == new H2Dialect().getDefaultDecimalPrecision()
    }

    void "the numeric_type_message table is actually created with valid DDL on H2"() {
        when:
        Map<String, Object> columns = [:]
        datastore.dataSource.connection.withCloseable { conn ->
            conn.createStatement().withCloseable { stmt ->
                stmt.executeQuery('''
                    select column_name, numeric_precision
                    from information_schema.columns
                    where upper(table_name) = 'NUMERIC_TYPE_MESSAGE'
                '''.stripIndent()).with { rs ->
                    while (rs.next()) {
                        columns[rs.getString('column_name').toUpperCase()] = rs.getInt('numeric_precision')
                    }
                }
            }
        }

        then: 'the table was not silently dropped, and every numeric column has a valid precision'
        columns.keySet().containsAll(['FLOAT_VALUE', 'DOUBLE_VALUE', 'BIG_DECIMAL_VALUE'])
        columns['FLOAT_VALUE'] > 0
        columns['DOUBLE_VALUE'] > 0
        columns['BIG_DECIMAL_VALUE'] > 0
    }
}

@Entity
class NumericTypeMessage implements HibernateEntity<NumericTypeMessage> {
    Float floatValue
    Double doubleValue
    BigDecimal bigDecimalValue
}

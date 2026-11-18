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
package org.grails.orm.hibernate.dialect

import org.hibernate.dialect.DatabaseVersion
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.dialect.SQLServerDialect
import spock.lang.Specification
import spock.lang.Unroll

class DialectKeyedSpec extends Specification {

    @Unroll
    void 'the constant for #dialectName is #expected'() {
        expect:
        DialectKeyed.forDialect(Shape, dialect) == expected

        where:
        dialect                                      | expected
        new PostgreSQLDialect()                      | Shape.POSTGRES
        new MySQLDialect()                           | Shape.MYSQL_FAMILY
        new MariaDBDialect()                         | Shape.MYSQL_FAMILY
        new H2Dialect()                              | Shape.EVERYTHING_ELSE
        new OracleDialect()                          | Shape.EVERYTHING_ELSE
        new Dialect(DatabaseVersion.make(1)) { }     | Shape.EVERYTHING_ELSE
        dialectName = dialect.class.simpleName
    }

    void 'a constant for a dialect class is chosen over the default whatever the order, and the default only when none is listed'() {
        expect:
        DialectKeyed.forDialect(Ordered, new SQLServerDialect()) == Ordered.SQL_SERVER
        DialectKeyed.forDialect(Ordered, new Dialect(DatabaseVersion.make(1)) { }) == Ordered.ANY
    }

    void 'a constant listing no dialect is never chosen, and an enum without a default rejects an unlisted dialect'() {
        when:
        DialectKeyed.forDialect(Partial, new H2Dialect())

        then:
        IllegalArgumentException e = thrown()
        e.message == "${Partial.name} has no constant for ${H2Dialect.name}"

        and:
        DialectKeyed.forDialect(Partial, new PostgreSQLDialect()) == Partial.POSTGRES
    }

    enum Shape implements DialectKeyed<Shape> {
        POSTGRES(PostgreSQLDialect),
        MYSQL_FAMILY(MySQLDialect, MariaDBDialect),
        EVERYTHING_ELSE(Dialect)

        private final Class<? extends Dialect>[] dialectClasses

        Shape(Class<? extends Dialect>... dialectClasses) {
            this.dialectClasses = dialectClasses
        }

        @Override
        Class<? extends Dialect>[] dialectClasses() {
            dialectClasses
        }
    }

    enum Ordered implements DialectKeyed<Ordered> {
        ANY(Dialect),
        SQL_SERVER(SQLServerDialect)

        private final Class<? extends Dialect>[] dialectClasses

        Ordered(Class<? extends Dialect>... dialectClasses) {
            this.dialectClasses = dialectClasses
        }

        @Override
        Class<? extends Dialect>[] dialectClasses() {
            dialectClasses
        }
    }

    enum Partial implements DialectKeyed<Partial> {
        NOT_A_DATABASE(),
        POSTGRES(PostgreSQLDialect)

        private final Class<? extends Dialect>[] dialectClasses

        Partial(Class<? extends Dialect>... dialectClasses) {
            this.dialectClasses = dialectClasses
        }

        @Override
        Class<? extends Dialect>[] dialectClasses() {
            dialectClasses
        }
    }
}

package org.grails.orm.hibernate.query

import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.MySQLDialect
import org.hibernate.dialect.MariaDBDialect
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.dialect.OracleDialect
import org.hibernate.dialect.SQLServerDialect
import spock.lang.Specification
import spock.lang.Unroll

class RegexDialectPatternSpec extends Specification {

    @Unroll
    void "test findPatternForDialect for #dialect.class.simpleName"() {
        expect:
        RegexDialectPattern.findPatternForDialect(dialect) == expectedPattern

        where:
        dialect                  | expectedPattern
        new MySQLDialect()       | "?1 RLIKE ?2"
        new MariaDBDialect()     | "?1 RLIKE ?2"
        new PostgreSQLDialect()  | "?1 ~ ?2"
        new OracleDialect()      | "REGEXP_LIKE(?1, ?2)"
        new H2Dialect()          | "REGEXP_LIKE(?1, ?2)"
        new SQLServerDialect()   | "?1 LIKE ?2" // Fallback
    }
}

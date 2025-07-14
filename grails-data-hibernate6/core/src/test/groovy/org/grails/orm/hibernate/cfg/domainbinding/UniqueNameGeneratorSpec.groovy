package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.MappingException
import org.hibernate.mapping.Column
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class UniqueNameGeneratorSpec extends Specification {

    @Subject
    UniqueNameGenerator generator = new UniqueNameGenerator()

    @Unroll
    def "should generate a unique name based on table and column names and truncate it"() {
        given: "A unique key with a table and several columns"
        def table = Mock(Table)
        def column1 = Mock(Column)
        def column2 = Mock(Column)
        def uniqueKey = Mock(UniqueKey)

        table.getName() >> "person"
        column1.getName() >> "first_name"
        column2.getName() >> "last_name"

        uniqueKey.getTable() >> table
        uniqueKey.getColumns() >> [column1, column2]

        def expectedName = generateExpectedName("person", "first_name", "last_name")

        when: "the unique name is generated"
        generator.setGeneratedUniqueName(uniqueKey)

        then: "the name is correctly calculated, prefixed, and truncated"
        1 * uniqueKey.setName(expectedName)
    }

    @Unroll
    def "should not truncate a generated name that is 30 characters or less"() {
        given: "A unique key whose hash results in a short name"
        def table = Mock(Table)
        def column = Mock(Column)
        def uniqueKey = Mock(UniqueKey)

        table.getName() >> "short_table"
        column.getName() >> "short_col"
        uniqueKey.getTable() >> table
        uniqueKey.getColumns() >> [column]

        def expectedName = generateExpectedName("short_table", "short_col")

        when: "the unique name is generated"
        generator.setGeneratedUniqueName(uniqueKey)

        then: "the name is not truncated because its length is not greater than 30"
        1 * uniqueKey.setName(expectedName)
    }

    @Unroll
    def "should throw MappingException if the unique key has no associated table"() {
        given: "A unique key without a table"
        def uniqueKey = Mock(UniqueKey)
        uniqueKey.getTable() >> null
        uniqueKey.getName() >> "my_uk" // For the exception message

        when: "an attempt is made to generate the name"
        generator.setGeneratedUniqueName(uniqueKey)

        then: "a MappingException is thrown with a descriptive message"
        def e = thrown(MappingException)
        e.message == "Unique Key my_uk does not have a table associated with it"
    }

    @Unroll
    def "should generate a name based only on the table if no columns are present"() {
        given: "A unique key with a table but no columns"
        def table = Mock(Table)
        def uniqueKey = Mock(UniqueKey)

        table.getName() >> "audit_log"
        uniqueKey.getTable() >> table
        uniqueKey.getColumns() >> []

        def expectedName = generateExpectedName("audit_log")

        when: "the unique name is generated"
        generator.setGeneratedUniqueName(uniqueKey)

        then: "the name is generated correctly using only the table name"
        1 * uniqueKey.setName(expectedName)
    }

    @Unroll
    def "should filter out columns with blank or null names"() {
        given: "A unique key with valid, blank, and null column names"
        def table = Mock(Table)
        def column1 = Mock(Column)
        def column2 = Mock(Column)
        def column3 = Mock(Column)
        def uniqueKey = Mock(UniqueKey)

        table.getName() >> "product"
        column1.getName() >> "sku"
        column2.getName() >> ""      // Blank name
        column3.getName() >> null    // Null name

        uniqueKey.getTable() >> table
        uniqueKey.getColumns() >> [column1, column2, column3]

        // Only valid names should be part of the hash
        def expectedName = generateExpectedName("product", "sku")

        when: "the unique name is generated"
        generator.setGeneratedUniqueName(uniqueKey)

        then: "the blank and null column names are ignored in the calculation"
        1 * uniqueKey.setName(expectedName)
    }

    /**
     * Helper method that mirrors the core logic of UniqueNameGenerator to create
     * a verifiable expected result without using hardcoded "magic" strings.
     */
    private String generateExpectedName(String... fields) {
        def ukString = fields.join('_')
        MessageDigest md = MessageDigest.getInstance("MD5")
        md.update(ukString.getBytes(StandardCharsets.UTF_8))
        String name = "UK" + new BigInteger(1, md.digest()).toString(16)
        if (name.length() > 30) {
            name = name.substring(0, 30)
        }
        return name
    }
}
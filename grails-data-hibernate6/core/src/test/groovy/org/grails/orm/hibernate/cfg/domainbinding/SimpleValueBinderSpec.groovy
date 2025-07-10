package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Table

class SimpleValueBinderSpec extends HibernateGormDatastoreSpec {

    void "Test defaults"() {
        when:
        def type = "String"
        def columnName = "columnName"
        def tableName = "table"
        def contributor = "contributor"
        def nullable = false
        def simpleValueBinder = new SimpleValueBinder()
        Table table = new Table(contributor,tableName);
        table.setName(tableName)
        def grailsDomainBinder = getGrailsDomainBinder()
        BasicValue simpleValue = new BasicValue(grailsDomainBinder.metadataBuildingContext, table);
        simpleValueBinder.bindSimpleValue(simpleValue, type, columnName, nullable)

        def column = (Column) simpleValue.column
        then:
        column
        column.value == simpleValue
        column.name == columnName
        !column.nullable
        simpleValue.column == column
        table.getColumn(0) == column
    }

    void "Test no table"() {
        when:
        def type = "String"
        def columnName = "columnName"
        def nullable = true
        def simpleValueBinder = new SimpleValueBinder()
        def grailsDomainBinder = getGrailsDomainBinder()
        BasicValue simpleValue = new BasicValue(grailsDomainBinder.metadataBuildingContext, null);
        simpleValueBinder.bindSimpleValue(simpleValue, type, columnName, nullable)

        def column = (Column) simpleValue.column
        then:
        column
        column.value == simpleValue
        column.name == columnName
        column.nullable
        simpleValue.column == column
        !simpleValue.table
    }
}

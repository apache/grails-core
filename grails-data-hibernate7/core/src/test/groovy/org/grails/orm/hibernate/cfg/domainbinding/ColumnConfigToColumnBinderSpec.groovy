package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.mapping.Column
import spock.lang.Specification

//TODO Check logic
class ColumnConfigToColumnBinderSpec extends Specification {

    def binder = new ColumnConfigToColumnBinder()
    def column = new Column("test")

    def "should bind column properties when values are valid"() {
        given:
        def columnConfig = new ColumnConfig()
        columnConfig.length = 100
        columnConfig.precision = 10
        columnConfig.scale = 2
        columnConfig.sqlType = "VARCHAR"
        columnConfig.unique = true

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, null)

        then:
        column.length == 100
        column.precision == 10
        column.scale == 2
        column.sqlType == "VARCHAR"
        column.unique
    }

    def "should not bind properties when values are -1"() {
        given:
        def columnConfig = new ColumnConfig()
        columnConfig.length = -1
        columnConfig.precision = -1
        columnConfig.scale = -1

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, null)

        then:
        column.length == null
        column.precision == null
        column.scale == null
        column.sqlType == null
        !column.unique
    }

    def "column config honors uniqueness property"() {
        given:
        def columnConfig = new ColumnConfig()
        columnConfig.length = -1
        columnConfig.precision = -1
        columnConfig.scale = -1
        PropertyConfig mappedForm = new PropertyConfig()
        mappedForm.setUnique("name")

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, mappedForm)

        then:
        column.length == null
        column.precision == null
        column.scale == null
        column.sqlType == null
        !column.unique
    }

    def "column config honors uniqueness property"() {
        given:
        def columnConfig = new ColumnConfig()
        columnConfig.length = -1
        columnConfig.precision = -1
        columnConfig.scale = -1
        PropertyConfig mappedForm = new PropertyConfig()

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, mappedForm)

        then:
        column.length == null
        column.precision == null
        column.scale == null
        column.sqlType == null
        !column.unique
    }
}
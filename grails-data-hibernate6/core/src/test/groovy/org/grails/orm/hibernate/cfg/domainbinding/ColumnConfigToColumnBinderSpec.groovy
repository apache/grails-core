package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.mapping.Column
import spock.lang.Specification

class ColumnConfigToColumnBinderSpec extends Specification {

    def binder = new ColumnConfigToColumnBinder()
    def column = Mock(Column)

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
        1 * column.setLength(100)
        1 * column.setPrecision(10)
        1 * column.setScale(2)
        1 * column.setSqlType("VARCHAR")
        0 * column.setUnique(_)
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
        0 * column.setLength(_)
        0 * column.setPrecision(_)
        0 * column.setScale(_)
        0 * column.setUnique(_)
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
        0 * column.setLength(_)
        0 * column.setPrecision(_)
        0 * column.setScale(_)
        0 * column.setUnique(_)
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
        0 * column.setLength(_)
        0 * column.setPrecision(_)
        0 * column.setScale(_)
        1 * column.setUnique(_)
    }
}
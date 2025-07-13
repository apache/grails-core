package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.hibernate.mapping.Column
import org.hibernate.mapping.Index
import org.hibernate.mapping.Table
import spock.lang.Specification

class IndexBinderSpec extends Specification {

    def indexBinder = new IndexBinder()
    def table = Mock(Table)
    def column = Mock(Column)
    def index = Mock(Index)

    def "should not create index when ColumnConfig is null"() {
        when:
        indexBinder.bindIndex("colName", column, null, table)

        then:
        0 * table._  // verifies no interactions with table
    }

    def "should create default index when index is true"() {
        given:
        def cc = new ColumnConfig()
        cc.index = true
        table.getName() >> "test_table"

        when:
        indexBinder.bindIndex("test_column", column, cc, table)

        then:
        1 * table.getOrCreateIndex("test_table_test_column_idx") >> index
        1 * index.addColumn(column)
    }

    def "should not create index when index is false"() {
        given:
        def cc = new ColumnConfig()
        cc.index = false

        when:
        indexBinder.bindIndex("test_column", column, cc, table)

        then:
        0 * table.getOrCreateIndex(_)
        0 * index.addColumn(_)
    }

    def "should create multiple indices when comma-separated string is provided"() {
        given:
        def cc = new ColumnConfig()
        cc.index = "idx_one,idx_two"

        when:
        indexBinder.bindIndex("test_column", column, cc, table)

        then:
        1 * table.getOrCreateIndex("idx_one") >> index
        1 * table.getOrCreateIndex("idx_two") >> index
        2 * index.addColumn(column)
    }

    def "should create single index when string value is provided"() {
        given:
        def cc = new ColumnConfig()
        cc.index = "custom_idx"

        when:
        indexBinder.bindIndex("test_column", column, cc, table)

        then:
        1 * table.getOrCreateIndex("custom_idx") >> index
        1 * index.addColumn(column)
    }

    def "should not create index when index value is null"() {
        given:
        def cc = new ColumnConfig()
        cc.index = null

        when:
        indexBinder.bindIndex("test_column", column, cc, table)

        then:
        0 * table.getOrCreateIndex(_)
        0 * index.addColumn(_)
    }
}
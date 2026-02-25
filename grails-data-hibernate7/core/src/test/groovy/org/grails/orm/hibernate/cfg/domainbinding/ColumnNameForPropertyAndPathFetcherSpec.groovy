package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

class ColumnNameForPropertyAndPathFetcherSpec extends Specification {

    def backticksRemover = new BackticksRemover()

    def "when grailsProp returns a column name then it is used"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover
        )

        def grailsProp = Mock(HibernatePersistentProperty)
        def cc = Mock(ColumnConfig)

        grailsProp.getColumnName(cc) >> "explicit_col"

        when:
        def result = fetcher.getColumnNameForPropertyAndPath(grailsProp, "somePath", cc)

        then:
        result == "explicit_col"
    }

    @Unroll
    def "when grailsProp returns null then builds from path '#path' and default column '#defaultCol' with backticks removed"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover
        )

        def grailsProp = Mock(HibernatePersistentProperty)

        grailsProp.getColumnName(null) >> null
        namingStrategy.resolveColumnName(path) >> resolvedPath
        defaultColumnFetcher.getDefaultColumnName(grailsProp) >> defaultCol

        when:
        def result = fetcher.getColumnNameForPropertyAndPath(grailsProp, path, null)

        then:
        result == expected

        where:
        path              | resolvedPath       | defaultCol           || expected
        "`order`"        | "`order`"          | "`customer_id`"      || "order_customer_id"
        "invoice"        | "invoice"          | "line_item_id"       || "invoice_line_item_id"
    }

    def "when grailsProp returns null and path is empty falls back to default column name only"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover
        )

        def grailsProp = Mock(HibernatePersistentProperty)

        grailsProp.getColumnName(null) >> null
        defaultColumnFetcher.getDefaultColumnName(grailsProp) >> "only_default"

        when:
        def result = fetcher.getColumnNameForPropertyAndPath(grailsProp, null, null)

        then:
        result == "only_default"
    }
}
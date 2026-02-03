package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import spock.lang.Specification
import spock.lang.Unroll

class ColumnNameForPropertyAndPathFetcherSpec extends Specification {

    def backticksRemover = new BackticksRemover()

    def "when ColumnConfig is null and mapping has explicit column then it is used"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover
        )

        def grailsProp = Mock(GrailsHibernatePersistentProperty)
        def owner = Mock(PersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)

        grailsProp.supportsJoinColumnMapping() >> false
        grailsProp.getMappedForm() >> pc
        pc.getColumn() >> "explicit_col"

        when:
        def result = fetcher.getColumnNameForPropertyAndPath(grailsProp, null, null)

        then:
        result == "explicit_col"
    }

    def "when ColumnConfig provided and join key mapping exists then join key name is used"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover
        )

        def grailsProp = Mock(GrailsHibernatePersistentProperty)
        def providedColumn = new ColumnConfig(name: "ignored_when_join_key")
        def pc = Mock(PropertyConfig)
        def joinTable = Mock(org.grails.orm.hibernate.cfg.JoinTable)
        def key = new ColumnConfig(name: "join_key_name")

        grailsProp.supportsJoinColumnMapping() >> true
        grailsProp.getMappedForm() >> pc
        pc.hasJoinKeyMapping() >> true
        pc.getJoinTable() >> joinTable
        joinTable.getKey() >> key

        when:
        def result = fetcher.getColumnNameForPropertyAndPath(grailsProp, null, providedColumn)

        then:
        result == "join_key_name"
    }

    @Unroll
    def "when no explicit column then builds from path '#path' and default column '#defaultCol' with backticks removed"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover
        )

        def grailsProp = Mock(PersistentProperty)

        // No config available and no join mapping path
        grailsProp.supportsJoinColumnMapping() >> false

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

    def "when path is empty falls back to default column name only"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover
        )

        def grailsProp = Mock(PersistentProperty)

        defaultColumnFetcher.getDefaultColumnName(grailsProp) >> "only_default"

        when:
        def result = fetcher.getColumnNameForPropertyAndPath(grailsProp, null, null)

        then:
        result == "only_default"
    }
}

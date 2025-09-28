package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import spock.lang.Specification
import spock.lang.Unroll

class TableNameFetcherSpec extends Specification {

    @Unroll
    def "Test getTableName when mapped form returns '#tableName'"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def hibernateEntityWrapper = Mock(HibernateEntityWrapper)
        def fetcher = new TableNameFetcher(namingStrategy, hibernateEntityWrapper)
        def persistentEntity = Mock(PersistentEntity)
        def mapping = Mock(Mapping)

        // Setup: getMappedForm always returns a valid Mapping object, per its contract
        hibernateEntityWrapper.getMappedForm(persistentEntity) >> mapping
        // The table name from the mapping can be explicit or null
        mapping.getTableName() >> tableName
        // The naming strategy will provide a fallback
        namingStrategy.resolveTableName(persistentEntity) >> "strategy_table_name"

        when:
        String result = fetcher.getTableName(persistentEntity)

        then:
        result == expectedResult

        where:
        scenario              | tableName             | expectedResult
        "explicit table name" | "explicit_table_name" | "explicit_table_name"
        "null table name"     | null                  | "strategy_table_name"
    }
}

package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.toone.One
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import spock.lang.Shared

class SingleEntityDataFetcherSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [One] }

    @Shared Long id

    void setupSpec() {
        One.withNewTransaction {
            id = new One().save(flush: true).id
        }
    }

    void "test get"() {
        given:
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment) {
            1 * getArgument('id') >> id
            1 * getMergedField()
        }
        SingleEntityDataFetcher fetcher = new SingleEntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))

        when:
        One one = fetcher.get(env)

        then:
        one != null
    }

    void "test supports"() {
        when:
        SingleEntityDataFetcher fetcher = new SingleEntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))

        then:
        !fetcher.supports(GraphQLDataFetcherType.CREATE)
        !fetcher.supports(GraphQLDataFetcherType.UPDATE)
        !fetcher.supports(GraphQLDataFetcherType.LIST)
        fetcher.supports(GraphQLDataFetcherType.GET)
        !fetcher.supports(GraphQLDataFetcherType.DELETE)
    }
}

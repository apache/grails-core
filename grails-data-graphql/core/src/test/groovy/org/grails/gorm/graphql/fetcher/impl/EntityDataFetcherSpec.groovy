package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.toone.One
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType

class EntityDataFetcherSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [One] }

    void setupSpec() {
        One.withNewTransaction {
            new One().save()
            new One().save()
            new One().save()
        }
    }

    void "test get"() {
        given:
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment) {
            1 * getArguments() >> [:]
            1 * getMergedField()
        }
        EntityDataFetcher fetcher = new EntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))

        when:
        List<One> ones = fetcher.get(env)

        then:
        ones.size() == 3
    }

    void "test pagination"() {
        given:
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment) {
            1 * getArguments() >> [max: 2, offset: 1]
            1 * getMergedField() 
        }
        EntityDataFetcher fetcher = new EntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))

        when:
        List<One> ones = fetcher.get(env)

        then:
        ones.size() == 2
    }

    void "test supports"() {
        when:
        EntityDataFetcher fetcher = new EntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))

        then:
        !fetcher.supports(GraphQLDataFetcherType.CREATE)
        !fetcher.supports(GraphQLDataFetcherType.UPDATE)
        fetcher.supports(GraphQLDataFetcherType.LIST)
        !fetcher.supports(GraphQLDataFetcherType.GET)
        !fetcher.supports(GraphQLDataFetcherType.DELETE)
    }
}

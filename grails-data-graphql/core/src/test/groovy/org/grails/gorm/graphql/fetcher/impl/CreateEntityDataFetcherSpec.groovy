package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.binding.GraphQLDataBinder
import org.grails.gorm.graphql.domain.general.toone.One
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import spock.lang.Subject

@Subject(CreateEntityDataFetcher)
class CreateEntityDataFetcherSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [One] }

    void "test get"() {
        given:
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment)
        GraphQLDataBinder binder = Mock(GraphQLDataBinder)
        CreateEntityDataFetcher fetcher = new CreateEntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))
        fetcher.dataBinder = binder

        when:
        fetcher.get(env)
        int count
        One.withNewSession {
            count = One.count
        }

        then:
        1 * env.getArgument('one') >> ['bar': 1]
        1 * binder.bind(_ as One, ['bar': 1])
        count == 1
    }

    void "test supports"() {
        when:
        CreateEntityDataFetcher fetcher = new CreateEntityDataFetcher<>(mappingContext.getPersistentEntity(One.name))

        then:
        fetcher.supports(GraphQLDataFetcherType.CREATE)
        !fetcher.supports(GraphQLDataFetcherType.UPDATE)
        !fetcher.supports(GraphQLDataFetcherType.LIST)
        !fetcher.supports(GraphQLDataFetcherType.GET)
        !fetcher.supports(GraphQLDataFetcherType.DELETE)
    }

}

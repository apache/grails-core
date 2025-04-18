package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.custom.OtherDomain
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.response.delete.DefaultGraphQLDeleteResponseHandler
import spock.lang.Shared

class SoftDeleteEntityDataFetcherSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [OtherDomain] }

    @Shared Long id

    void setupSpec() {
        OtherDomain.withNewTransaction {
            id = new OtherDomain(name: "Sally").save(flush: true).id
        }
    }

    void "test get"() {
        given:
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment) {
            1 * getArgument('id') >> id
            1 * getMergedField()
        }
        SoftDeleteEntityDataFetcher fetcher = new SoftDeleteEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name), 'name', 'John')
        fetcher.responseHandler = new DefaultGraphQLDeleteResponseHandler()

        when:
        Map result = fetcher.get(env)
        OtherDomain other
        OtherDomain.withNewSession {
            other = OtherDomain.get(id)
        }

        then:
        result.success
        other.name == 'John'
    }

    void "test supports"() {
        when:
        SoftDeleteEntityDataFetcher fetcher = new SoftDeleteEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name), 'name', 'John')

        then:
        !fetcher.supports(GraphQLDataFetcherType.CREATE)
        !fetcher.supports(GraphQLDataFetcherType.UPDATE)
        !fetcher.supports(GraphQLDataFetcherType.LIST)
        !fetcher.supports(GraphQLDataFetcherType.GET)
        fetcher.supports(GraphQLDataFetcherType.DELETE)
    }
}

package org.grails.gorm.graphql.fetcher.impl

import grails.gorm.transactions.Transactional
import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.binding.GraphQLDataBinder
import org.grails.gorm.graphql.binding.manager.DefaultGraphQLDataBinderManager
import org.grails.gorm.graphql.domain.general.custom.OtherDomain
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType

class UpdateEntityDataFetcherSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [OtherDomain] }

    @Transactional
    OtherDomain createInstance() {
        new OtherDomain(name: 'John').save()
    }

    void "test get"() {
        given:
        OtherDomain other = createInstance()
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment)
        GraphQLDataBinder binder = new DefaultGraphQLDataBinderManager().getDataBinder(Object)
        UpdateEntityDataFetcher fetcher = new UpdateEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name))
        fetcher.dataBinder = binder

        when:
        fetcher.get(env)
        OtherDomain updated
        OtherDomain.withNewSession {
            updated = OtherDomain.get(other.id)
        }

        then:
        1 * env.getArgument('id') >> other.id
        1 * env.getArgument('otherDomain') >> ['name': 'Sally']
        updated.name == 'Sally'
    }

    void "test optimistic locking"() {
        given:
        OtherDomain other = createInstance()
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment)
        GraphQLDataBinder binder = new DefaultGraphQLDataBinderManager().getDataBinder(Object)
        UpdateEntityDataFetcher fetcher = new UpdateEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name))
        fetcher.dataBinder = binder

        when:
        OtherDomain updated = fetcher.get(env)

        then:
        1 * env.getArgument('id') >> other.id
        1 * env.getArgument('otherDomain') >> ['name': 'Sally', 'version': -1L]
        updated.name == 'John'
        updated.errors.hasFieldErrors('version')
    }


    void "test optimistic locking with null version"() {
        given:
        OtherDomain other = createInstance()
        DataFetchingEnvironment env = Mock(DataFetchingEnvironment)
        GraphQLDataBinder binder = new DefaultGraphQLDataBinderManager().getDataBinder(Object)
        UpdateEntityDataFetcher fetcher = new UpdateEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name))
        fetcher.dataBinder = binder

        when:
        OtherDomain updated = fetcher.get(env)

        then:
        1 * env.getArgument('id') >> other.id
        1 * env.getArgument('otherDomain') >> ['name': 'Sally', 'version': null]
        updated.name == 'Sally'
        !updated.hasErrors()
    }

    void "test supports"() {
        when:
        UpdateEntityDataFetcher fetcher = new UpdateEntityDataFetcher<>(mappingContext.getPersistentEntity(OtherDomain.name))

        then:
        !fetcher.supports(GraphQLDataFetcherType.CREATE)
        fetcher.supports(GraphQLDataFetcherType.UPDATE)
        !fetcher.supports(GraphQLDataFetcherType.LIST)
        !fetcher.supports(GraphQLDataFetcherType.GET)
        !fetcher.supports(GraphQLDataFetcherType.DELETE)
    }

}

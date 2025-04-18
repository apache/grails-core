package org.grails.gorm.graphql.fetcher.manager

import graphql.schema.DataFetchingEnvironment
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.binding.GraphQLDataBinder
import org.grails.gorm.graphql.fetcher.BindingGormDataFetcher
import org.grails.gorm.graphql.fetcher.DeletingGormDataFetcher
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.fetcher.ReadingGormDataFetcher
import org.grails.gorm.graphql.response.delete.GraphQLDeleteResponseHandler
import spock.lang.Shared
import spock.lang.Specification

import static org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType.*

class GraphQLDataFetcherManagerSpec extends Specification {

    @Shared GraphQLDataFetcherManager manager
    @Shared ReadingGormDataFetcher mockReadingFetcher
    @Shared BindingGormDataFetcher mockBindingFetcher
    @Shared DeletingGormDataFetcher mockDeletingFetcher

    PersistentEntity getMockEntity(Class clazz) {
        Stub(PersistentEntity) {
            getJavaClass() >> clazz
            getAssociations() >> []
        }
    }

    void setupSpec() {
        manager = new DefaultGraphQLDataFetcherManager()
        mockReadingFetcher = new ReadingGormDataFetcher() {
            @Override
            boolean supports(GraphQLDataFetcherType type) {
                return false
            }

            @Override
            Object get(DataFetchingEnvironment environment) {
                return null
            }
        }
        mockBindingFetcher = new BindingGormDataFetcher() {
            GraphQLDataBinder dataBinder

            @Override
            boolean supports(GraphQLDataFetcherType type) {
                return false
            }

            @Override
            Object get(DataFetchingEnvironment environment) {
                return null
            }
        }
        mockDeletingFetcher = new DeletingGormDataFetcher() {
            GraphQLDeleteResponseHandler responseHandler

            @Override
            boolean supports(GraphQLDataFetcherType type) {
                return false
            }

            @Override
            Object get(DataFetchingEnvironment environment) {
                return null
            }
        }
    }

    void "test building a manager with the wrong types"() {
        when:
        new DefaultGraphQLDataFetcherManager([
                (type): fetcher
        ])

        then:
        thrown(IllegalArgumentException)

        where:
        type   | fetcher
        DELETE | mockReadingFetcher
        CREATE | mockReadingFetcher
        UPDATE | mockReadingFetcher

        DELETE | mockBindingFetcher
        GET    | mockBindingFetcher
        LIST   | mockBindingFetcher
        COUNT  | mockBindingFetcher

        LIST   | mockDeletingFetcher
        COUNT  | mockDeletingFetcher
        GET    | mockDeletingFetcher
        CREATE | mockDeletingFetcher
        UPDATE | mockDeletingFetcher
    }

    void "test building a manager with correct types"() {
        when:
        new DefaultGraphQLDataFetcherManager([
                (DELETE): mockDeletingFetcher,
                (CREATE): mockBindingFetcher,
                (UPDATE): mockBindingFetcher,
                (GET): mockReadingFetcher,
                (LIST): mockReadingFetcher,
                (COUNT): mockReadingFetcher
        ])

        then:
        noExceptionThrown()
        GraphQLDataFetcherType.values().size() == 6
    }

    void "test get binding fetcher with wrong argument type"() {
        when:
        manager.getBindingFetcher(getMockEntity(String), type)

        then:
        thrown(IllegalArgumentException)

        where:
        type << [GET, LIST, DELETE]
    }

    void "test get reading fetcher with wrong argument type"() {
        when:
        manager.getReadingFetcher(getMockEntity(String), type)

        then:
        thrown(IllegalArgumentException)

        where:
        type << [CREATE, UPDATE, DELETE]
    }

    void "test registering a binding fetcher"() {
        given:
        GormEnhancer.STATIC_APIS.put(ConnectionSource.DEFAULT, ['java.lang.String': Mock(GormStaticApi)])

        when:
        manager.registerBindingDataFetcher(String, mockBindingFetcher)

        then: "the binder is not registered because it doesn't support any type"
        !manager.getBindingFetcher(getMockEntity(String), CREATE).isPresent()
        !manager.getBindingFetcher(getMockEntity(String), UPDATE).isPresent()
    }

    void "test fetchers for the exact class are resolved first (binding)"() {
        GraphQLDataFetcherManager manager = new DefaultGraphQLDataFetcherManager([
                (DELETE): mockDeletingFetcher,
                (CREATE): mockBindingFetcher,
                (UPDATE): mockBindingFetcher,
                (GET): mockReadingFetcher,
                (LIST): mockReadingFetcher,
                (COUNT): mockReadingFetcher
        ])
        BindingGormDataFetcher myBindingFetcher = new BindingGormDataFetcher() {
            GraphQLDataBinder dataBinder

            @Override
            boolean supports(GraphQLDataFetcherType type) {
                type == CREATE
            }

            @Override
            Object get(DataFetchingEnvironment environment) { null }
        }

        expect: 'The default binders are returned'
        manager.getBindingFetcher(getMockEntity(String), CREATE).get() == mockBindingFetcher
        manager.getBindingFetcher(getMockEntity(String), UPDATE).get() == mockBindingFetcher

        when: 'A binder is registered for String that supports CREATE'
        manager.registerBindingDataFetcher(String, myBindingFetcher)

        then: 'The binder is returned for CREATE, and the Object binder is returned for update'
        manager.getBindingFetcher(getMockEntity(String), CREATE).get() == myBindingFetcher

        when: 'The manager has no default binders'
        manager = new DefaultGraphQLDataFetcherManager()

        then: 'null is returned'
        !manager.getBindingFetcher(getMockEntity(String), CREATE).isPresent()
        !manager.getBindingFetcher(getMockEntity(String), UPDATE).isPresent()

        when: 'A custom binder is registered'
        manager.registerBindingDataFetcher(String, myBindingFetcher)

        then: 'The binder is returned'
        manager.getBindingFetcher(getMockEntity(String), CREATE).get() == myBindingFetcher
    }

    void "test fetchers for the exact class are resolved first (reading)"() {
        GraphQLDataFetcherManager manager = new DefaultGraphQLDataFetcherManager([
                (DELETE): mockDeletingFetcher,
                (CREATE): mockBindingFetcher,
                (UPDATE): mockBindingFetcher,
                (GET): mockReadingFetcher,
                (LIST): mockReadingFetcher,
                (COUNT): mockReadingFetcher
        ])
        ReadingGormDataFetcher myReadingFetcher = new ReadingGormDataFetcher() {
            @Override
            boolean supports(GraphQLDataFetcherType type) {
                type == GET
            }

            @Override
            Object get(DataFetchingEnvironment environment) { null }
        }


        expect: 'The default binders are returned'
        manager.getReadingFetcher(getMockEntity(String), GET).get() == mockReadingFetcher
        manager.getReadingFetcher(getMockEntity(String), LIST).get() == mockReadingFetcher
        manager.getReadingFetcher(getMockEntity(String), COUNT).get() == mockReadingFetcher

        when: 'A binder is registered for String that supports GET'
        manager.registerReadingDataFetcher(String, myReadingFetcher)

        then: 'The binder is returned for GET, and the Object binder is returned for update'
        manager.getReadingFetcher(getMockEntity(String), GET).get() == myReadingFetcher

        when: 'The manager has no default binders'
        manager = new DefaultGraphQLDataFetcherManager()

        then: 'null is returned'
        !manager.getReadingFetcher(getMockEntity(String), GET).isPresent()
        !manager.getReadingFetcher(getMockEntity(String), LIST).isPresent()
        !manager.getReadingFetcher(getMockEntity(String), COUNT).isPresent()

        when: 'A custom binder is registered'
        manager.registerReadingDataFetcher(String, myReadingFetcher)

        then: 'The binder is returned'
        manager.getReadingFetcher(getMockEntity(String), GET).get() == myReadingFetcher
    }

    void "test fetchers for the exact class are resolved first (deleting)"() {
        GraphQLDataFetcherManager manager = new DefaultGraphQLDataFetcherManager([
                (DELETE): mockDeletingFetcher,
                (CREATE): mockBindingFetcher,
                (UPDATE): mockBindingFetcher,
                (GET): mockReadingFetcher,
                (LIST): mockReadingFetcher,
                (COUNT): mockReadingFetcher
        ])
        DeletingGormDataFetcher myDeletingFetcher = new FakeDeletingFetcher()

        expect: 'The default binders are returned'
        manager.getDeletingFetcher(getMockEntity(String)).get() == mockDeletingFetcher

        when: 'A binder is registered for String that supports CREATE'
        manager.registerDeletingDataFetcher(String, myDeletingFetcher)

        then: 'The binder is returned for CREATE, and the Object binder is returned for update'
        manager.getDeletingFetcher(getMockEntity(String)).get() == myDeletingFetcher

        when: 'The manager has no default binders'
        manager = new DefaultGraphQLDataFetcherManager()

        then: 'null is returned'
        !manager.getDeletingFetcher(getMockEntity(String)).isPresent()

        when: 'A custom binder is registered'
        manager.registerDeletingDataFetcher(String, myDeletingFetcher)

        then: 'The binder is returned'
        manager.getDeletingFetcher(getMockEntity(String)).get() == myDeletingFetcher
    }

    class FakeDeletingFetcher implements DeletingGormDataFetcher {

        GraphQLDeleteResponseHandler responseHandler

        @Override
        Object get(DataFetchingEnvironment environment) {
            return null
        }
    }
}

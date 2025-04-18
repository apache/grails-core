package org.grails.gorm.graphql.fetcher.impl

import graphql.language.Field
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.GraphQLServiceManager
import org.grails.gorm.graphql.entity.operations.OperationType
import org.grails.gorm.graphql.fetcher.interceptor.CustomMutationInterceptorInvoker
import org.grails.gorm.graphql.fetcher.interceptor.CustomQueryInterceptorInvoker
import org.grails.gorm.graphql.fetcher.interceptor.InterceptingDataFetcher
import org.grails.gorm.graphql.fetcher.interceptor.InterceptorInvoker
import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor
import org.grails.gorm.graphql.interceptor.manager.GraphQLInterceptorManager
import spock.lang.Specification

class InterceptingDataFetcherSpec extends Specification {

    private DataFetchingEnvironment buildMockEnvironment() {
        Stub(DataFetchingEnvironment) {
            getFields() >> [Stub(Field) {
                getName() >> 'foo'
            }]
        }
    }

    void "test interceptors are invoked for QUERY"() {
        given:
        DataFetchingEnvironment environment = buildMockEnvironment()

        GraphQLInterceptorManager interceptorManager = Mock(GraphQLInterceptorManager) {
            1 * getInterceptors(String) >> [Mock(GraphQLFetcherInterceptor) {
                1 * onCustomQuery('foo', environment) >> true
            }]
        }
        GraphQLServiceManager serviceManager = Mock(GraphQLServiceManager) {
            1 * getService(GraphQLInterceptorManager) >> interceptorManager
        }
        InterceptorInvoker interceptorInvoker = new CustomQueryInterceptorInvoker()
        DataFetcher wrappedFetcher = Mock(DataFetcher)

        when:
        def fetcher = new InterceptingDataFetcher(String, serviceManager, interceptorInvoker, null, wrappedFetcher)
        fetcher.get(environment)

        then:
        1 * wrappedFetcher.get(environment)
    }

    void "test interceptors are invoked for MUTATION"() {
        given:
        DataFetchingEnvironment environment = buildMockEnvironment()
        GraphQLInterceptorManager interceptorManager = Mock(GraphQLInterceptorManager) {
            1 * getInterceptors(String) >> [Mock(GraphQLFetcherInterceptor) {
                1 * onCustomMutation('foo', environment) >> true
            }]
        }
        GraphQLServiceManager serviceManager = Mock(GraphQLServiceManager) {
            1 * getService(GraphQLInterceptorManager) >> interceptorManager
        }
        InterceptorInvoker interceptorInvoker = new CustomMutationInterceptorInvoker()
        DataFetcher wrappedFetcher = Mock(DataFetcher)

        when:
        def fetcher = new InterceptingDataFetcher(String, serviceManager, interceptorInvoker, null, wrappedFetcher)
        fetcher.get(environment)

        then:
        1 * wrappedFetcher.get(environment)
    }


    void "test execution is prevented"() {
        given:
        DataFetchingEnvironment environment = buildMockEnvironment()
        GraphQLInterceptorManager interceptorManager = Mock(GraphQLInterceptorManager) {
            1 * getInterceptors(String) >> [Mock(GraphQLFetcherInterceptor) {
                1 * onCustomMutation('foo', environment) >> true
            },Mock(GraphQLFetcherInterceptor) {
                1 * onCustomMutation('foo', environment) >> false
            },Mock(GraphQLFetcherInterceptor) {
                0 * onCustomMutation('foo', environment) >> true
            }]
        }
        GraphQLServiceManager serviceManager = Mock(GraphQLServiceManager) {
            1 * getService(GraphQLInterceptorManager) >> interceptorManager
        }
        InterceptorInvoker interceptorInvoker = new CustomMutationInterceptorInvoker()
        DataFetcher wrappedFetcher = Mock(DataFetcher)

        when:
        def fetcher = new InterceptingDataFetcher(String, serviceManager, interceptorInvoker, null, wrappedFetcher)
        def result = fetcher.get(environment)

        then:
        0 * wrappedFetcher.get(environment)
        result == null
    }

}

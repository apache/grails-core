package org.grails.gorm.graphql.interceptor.manager

import org.grails.datastore.mapping.core.Ordered
import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor
import org.grails.gorm.graphql.interceptor.impl.BaseGraphQLFetcherInterceptor
import spock.lang.Specification

class DefaultGraphQLInterceptorManagerSpec extends Specification {

    GraphQLInterceptorManager manager

    void setup() {
        manager = new DefaultGraphQLInterceptorManager()
    }

    void "test interceptors are retrieved for the class searched and super classes"() {
        when:
        manager.registerInterceptor(Collection, new BaseGraphQLFetcherInterceptor())
        manager.registerInterceptor(Collection, new BaseGraphQLFetcherInterceptor())
        manager.registerInterceptor(List, new BaseGraphQLFetcherInterceptor())
        manager.registerInterceptor(ArrayList, new BaseGraphQLFetcherInterceptor())

        then:
        manager.getInterceptors(ArrayList).size() == 4
    }

    void "test interceptors are retrieved in order"() {
        given:
        GraphQLFetcherInterceptor nonOrdered = new BaseGraphQLFetcherInterceptor()
        GraphQLFetcherInterceptor order4 = new OrderedBaseGraphQLInterceptor(4)
        GraphQLFetcherInterceptor order6 = new OrderedBaseGraphQLInterceptor(6)
        GraphQLFetcherInterceptor order8 = new OrderedBaseGraphQLInterceptor(8)

        when:
        manager.registerInterceptor(Collection, order8)
        manager.registerInterceptor(Collection, nonOrdered)
        manager.registerInterceptor(List, order4)
        manager.registerInterceptor(ArrayList, order6)

        then:
        manager.getInterceptors(ArrayList) == [order4, order6, order8, nonOrdered]
    }

    void "test register with a null class"() {
        when:
        manager.registerInterceptor(null, new BaseGraphQLFetcherInterceptor())

        then:
        thrown(IllegalArgumentException)
    }

    void "test register with a null interceptor"() {
        when:
        manager.registerInterceptor(String, null)

        then:
        thrown(IllegalArgumentException)
    }

    class OrderedBaseGraphQLInterceptor extends BaseGraphQLFetcherInterceptor implements Ordered {

        int order

        OrderedBaseGraphQLInterceptor(int order) {
            this.order = order
        }
    }
}

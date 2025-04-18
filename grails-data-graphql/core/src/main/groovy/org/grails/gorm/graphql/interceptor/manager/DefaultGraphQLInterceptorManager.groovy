package org.grails.gorm.graphql.interceptor.manager

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.order.OrderedComparator
import org.grails.gorm.graphql.interceptor.GraphQLFetcherInterceptor
import org.grails.gorm.graphql.interceptor.GraphQLSchemaInterceptor
import org.grails.gorm.graphql.types.KeyClassQuery

/**
 * Default implementation of {@link GraphQLInterceptorManager} that
 * will also return a result if the class requested is a subclass
 * of a class that exists in the registry. All interceptors for the
 * exact class searched and any parent classes will be returned. Multiple
 * interceptors for the same class can be registered.
 *
 * Example:
 * registerInterceptor(Collection, interceptor1)
 * registerInterceptor(Collection, interceptor2)
 * registerInterceptor(List, interceptor3)
 *
 * If an ArrayList is being intercepted, all 3 interceptors will fire
 *
 * The resulting list will be sorted based on order. Implement the
 * {@link org.grails.datastore.mapping.core.Ordered} trait to
 * control the order of your interceptors.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class DefaultGraphQLInterceptorManager implements GraphQLInterceptorManager, KeyClassQuery<List<GraphQLFetcherInterceptor>> {

    protected Map<Class, List<GraphQLFetcherInterceptor>> interceptors = Collections.synchronizedMap([:]).withDefault { [] }

    protected List<GraphQLSchemaInterceptor> schemaInterceptors = []

    protected Comparator interceptorComparator = new OrderedComparator<>()

    /**
     * @see GraphQLInterceptorManager#registerInterceptor
     */
    @Override
    void registerInterceptor(Class type, GraphQLFetcherInterceptor interceptor) {
        if (type == null) {
            throw new IllegalArgumentException('Cannot register an interceptor for a null type')
        }
        if (interceptor == null) {
            throw new IllegalArgumentException('Registering a null interceptor is not allowed')
        }
        interceptors.get(type).add(interceptor)
    }

    @Override
    void registerInterceptor(GraphQLSchemaInterceptor interceptor) {
        schemaInterceptors.add(interceptor)
    }

    /**
     * @see GraphQLInterceptorManager#getInterceptors
     *
     * @return NULL if no interceptors found
     */
    @Override
    List<GraphQLFetcherInterceptor> getInterceptors(Class clazz) {
        searchMapAll(interceptors, clazz).<GraphQLFetcherInterceptor> sort(true, interceptorComparator)
    }

    @Override
    List<GraphQLSchemaInterceptor> getInterceptors() {
        schemaInterceptors.<GraphQLSchemaInterceptor> sort(true, interceptorComparator)
    }
}

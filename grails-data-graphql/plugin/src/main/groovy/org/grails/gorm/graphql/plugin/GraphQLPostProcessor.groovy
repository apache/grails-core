package org.grails.gorm.graphql.plugin

import groovy.transform.CompileStatic
import org.grails.gorm.graphql.binding.manager.GraphQLDataBinderManager
import org.grails.gorm.graphql.fetcher.manager.GraphQLDataFetcherManager
import org.grails.gorm.graphql.interceptor.manager.GraphQLInterceptorManager
import org.grails.gorm.graphql.types.GraphQLTypeManager
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanPostProcessor

@CompileStatic
class GraphQLPostProcessor implements BeanPostProcessor {

    @Override
    Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        bean
    }

    @Override
    Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof GraphQLTypeManager) {
            doWith((GraphQLTypeManager)bean)
        }
        else if (bean instanceof GraphQLDataBinderManager) {
            doWith((GraphQLDataBinderManager)bean)
        }
        else if (bean instanceof GraphQLDataFetcherManager) {
            doWith((GraphQLDataFetcherManager)bean)
        }
        else if (bean instanceof GraphQLInterceptorManager) {
            doWith((GraphQLInterceptorManager)bean)
        }
        bean
    }

    void doWith(GraphQLTypeManager typeManager) {}

    void doWith(GraphQLDataBinderManager dataBinderManager) {}

    void doWith(GraphQLDataFetcherManager dataFetcherManager) {}

    void doWith(GraphQLInterceptorManager interceptorManager) {}
}

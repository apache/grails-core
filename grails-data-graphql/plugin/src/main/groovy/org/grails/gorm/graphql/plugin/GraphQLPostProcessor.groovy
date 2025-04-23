/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

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

/*
 * Copyright 2010 Luke Daley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.spring;

import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;

/**
 * Allows the type of the underlying object to be specified explicitly.
 *
 * This is used when creating scoped proxies of transactional proxies of services.
 * The scoped proxy needs to know that type of the object before the transactional proxy
 * factory has instantiated the underlying service and is able to determine it's class. This
 * class allows the type to be explicitly specified.
 *
 * Used by org.codehaus.groovy.grails.plugins.services.ServicesGrailsPlugin.
 */
@SuppressWarnings("serial")
public class TypeSpecifyableTransactionProxyFactoryBean extends TransactionProxyFactoryBean {

    private Class<?> type;

    public TypeSpecifyableTransactionProxyFactoryBean(Class<?> type) {
        this.type = type;
    }

    @Override
    public Class<?> getObjectType() {
        if (type != null) {
            return type;
        }

        return super.getObjectType();
    }
}

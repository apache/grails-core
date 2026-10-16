/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.support

import groovy.transform.CompileStatic

import grails.persistence.support.PersistenceContextInterceptor

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class AggregatePersistenceContextInterceptor implements PersistenceContextInterceptor {

    private final List<PersistenceContextInterceptor> interceptors

    /**
     * Constructor.
     * @param interceptors the real interceptors
     */
    AggregatePersistenceContextInterceptor(final List<PersistenceContextInterceptor> interceptors) {
        this.interceptors = interceptors
    }

    boolean isOpen() {
        for (PersistenceContextInterceptor interceptor in interceptors) {
            if (interceptor.isOpen()) {
                // true at least one is true
                return true
            }
        }
        return false
    }

    void reconnect() {
        for (PersistenceContextInterceptor interceptor in interceptors) {
            interceptor.reconnect()
        }
    }

    void destroy() {
        for (PersistenceContextInterceptor interceptor in interceptors) {
            try {
                if (interceptor.isOpen()) {
                    interceptor.destroy()
                }
            } catch (Exception ignored) {
                // ignore exception
            }
        }
    }

    void clear() {
        for (PersistenceContextInterceptor interceptor in interceptors) {
            interceptor.clear()
        }
    }

    void disconnect() {
        for (PersistenceContextInterceptor interceptor in interceptors) {
            interceptor.disconnect()
        }
    }

    void flush() {
        for (PersistenceContextInterceptor interceptor in interceptors) {
            interceptor.flush()
        }
    }

    void init() {
        for (PersistenceContextInterceptor interceptor in interceptors) {
            interceptor.init()
        }
    }

    void setReadOnly() {
        for (PersistenceContextInterceptor interceptor in interceptors) {
            interceptor.setReadOnly()
        }
    }

    void setReadWrite() {
        for (PersistenceContextInterceptor interceptor in interceptors) {
            interceptor.setReadWrite()
        }
    }

}

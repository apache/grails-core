/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.support;

import grails.persistence.support.PersistenceContextInterceptor;

import java.util.List;


/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class AggregatePersistenceContextInterceptor implements PersistenceContextInterceptor {

    private final List<PersistenceContextInterceptor> interceptors;

    /**
     * Constructor.
     * @param interceptors the real interceptors
     */
    public AggregatePersistenceContextInterceptor(final List<PersistenceContextInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public boolean isOpen() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            if (interceptor.isOpen()) {
                // true at least one is true
                return true;
            }
        }
        return false;
    }

    public void reconnect() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.reconnect();
        }
    }

    public void destroy() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            try {
                if (interceptor.isOpen()) {
                    interceptor.destroy();
                }
            } catch (Exception e) {
                // ignore exception
            }
        }
    }

    public void clear() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.clear();
        }
    }

    public void disconnect() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.disconnect();
        }
    }

    public void flush() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.flush();
        }
    }

    public void init() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.init();
        }
    }

    public void setReadOnly() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.setReadOnly();
        }
    }

    public void setReadWrite() {
        for (PersistenceContextInterceptor interceptor : interceptors) {
            interceptor.setReadWrite();
        }
    }
}

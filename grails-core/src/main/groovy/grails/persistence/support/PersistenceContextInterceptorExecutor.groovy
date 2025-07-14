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
package grails.persistence.support

import groovy.transform.CompileStatic
import org.springframework.context.ApplicationContext

/**
 * Executes persistence context interceptors phases.
 *
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class PersistenceContextInterceptorExecutor {

    Collection<PersistenceContextInterceptor> persistenceContextInterceptors

    PersistenceContextInterceptorExecutor(Collection<PersistenceContextInterceptor> persistenceContextInterceptors) {
        this.persistenceContextInterceptors = persistenceContextInterceptors
    }

    void initPersistenceContext() {
        initPersistenceContextInternal(persistenceContextInterceptors)
    }

    void destroyPersistenceContext() {
        destroyPersistenceContextInternal(persistenceContextInterceptors)
    }

    static void initPersistenceContext(ApplicationContext appCtx) {
        if (appCtx) {
            final interceptors = appCtx.getBeansOfType(PersistenceContextInterceptor).values()
            initPersistenceContextInternal(interceptors)
        }
    }

    private static void initPersistenceContextInternal(Collection<PersistenceContextInterceptor> interceptors) {
        for (PersistenceContextInterceptor i in interceptors) {
            i.init()
        }
    }

    static void destroyPersistenceContext(ApplicationContext appCtx) {
        if (appCtx) {
            final interceptors = appCtx.getBeansOfType(PersistenceContextInterceptor).values()
            destroyPersistenceContextInternal(interceptors)
        }
    }

    private static void destroyPersistenceContextInternal(Collection<PersistenceContextInterceptor> interceptors) {
        for (PersistenceContextInterceptor i in interceptors) {
            try {
                i.destroy()
            } catch (e) {
                // ignore exception
            }
        }
    }

}

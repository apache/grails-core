/*
 * Copyright 2013 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.async.services

import groovy.transform.CompileStatic
import grails.persistence.support.PersistenceContextInterceptorExecutor
import grails.async.decorator.PromiseDecorator

/**
 * A {@link PromiseDecorator} that wraps a promise execution in a persistence context (example Hibernate session)
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class PersistenceContextPromiseDecorator implements PromiseDecorator{
    PersistenceContextInterceptorExecutor persistenceContextInterceptorExecutor

    PersistenceContextPromiseDecorator(PersistenceContextInterceptorExecutor persistenceContextInterceptorExecutor) {
        this.persistenceContextInterceptorExecutor = persistenceContextInterceptorExecutor
    }

    @Override
    <D> Closure<D> decorate(Closure<D> original) {
        if (persistenceContextInterceptorExecutor != null) {
            return { args ->
                try {
                    persistenceContextInterceptorExecutor.initPersistenceContext()
                    return original.call(args)
                } finally {
                    persistenceContextInterceptorExecutor.destroyPersistenceContext()
                }
            }
        }
        return original
    }
}

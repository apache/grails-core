/* Copyright (C) 2013 SpringSource
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
package org.grails.datastore.gorm.async

import grails.async.Promise
import grails.async.Promises
import grails.async.decorator.PromiseDecorator
import grails.async.decorator.PromiseDecoratorProvider
import grails.gorm.api.GormStaticOperations
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.async.transform.DelegateAsync

/**
 * Transforms the GormStaticApi into an asynchronous API
 *
 * @author Graeme Rocher
 * @since 2.3
 */
class GormAsyncStaticApi<D> implements PromiseDecoratorProvider{

    @DelegateAsync GormStaticOperations<D> staticApi

    /**
     * Wraps each promise in a new persistence session
     */
    private List<PromiseDecorator> decorators = [ { Closure callable ->
        return { args -> staticApi.withNewSession{ callable.call(*args) } }
    } as PromiseDecorator ]

    GormAsyncStaticApi(GormStaticApi<D> staticApi) {
        this.staticApi = staticApi
    }

    @Override
    @CompileStatic
    List<PromiseDecorator> getDecorators() {
        this.decorators
    }

    /**
     * Used to perform a sequence of operations asynchronously
     * @param callable The callable
     * @return The promise
     */
    @CompileStatic
    public <T> Promise<T> task(Closure<T> callable) {
        callable.delegate = staticApi.gormPersistentEntity.javaClass
        (Promise<T>)Promises.createPromise(callable, decorators)
    }
}
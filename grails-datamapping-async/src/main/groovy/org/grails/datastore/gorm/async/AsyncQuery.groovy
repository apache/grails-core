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

import grails.async.decorator.PromiseDecorator
import grails.async.decorator.PromiseDecoratorProvider
import org.grails.datastore.gorm.async.transform.DelegateAsync
import org.grails.datastore.gorm.query.GormOperations

/**
 * Exposes all methods from the {@link GormOperations} interface asynchronously
 *
 * @author Graeme Rocher
 * @since 2.3
 */
class AsyncQuery<E> implements PromiseDecoratorProvider {

    @DelegateAsync GormOperations<E> gormOperations

    /**
     * Wraps each promise in a new persistence session
     */
    private List<PromiseDecorator> decorators = [ { Closure callable ->
        return { args ->
            gormOperations.persistentClass.withNewSession {
                callable.call(*args)
            }
        }
    } as PromiseDecorator ]


    AsyncQuery(GormOperations<E> gormOperations) {
        this.gormOperations = gormOperations
    }

    @Override
    List<PromiseDecorator> getDecorators() {
        return decorators
    }
}

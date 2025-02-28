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
package org.grails.compiler.web.async

import grails.async.services.TransactionalPromiseDecorator
import groovy.transform.CompileStatic
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional

import java.lang.reflect.Method

/**
 * Utility methods for use by Async transformations
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class TransactionalAsyncTransformUtils {


    /**
     * Creates a {@link TransactionalPromiseDecorator} for the given transactionManager and method to be invoked
     *
     * @param transactionManager The transactionManager
     * @param method The method
     * @return A TransactionalPromiseDecorator
     */
    static TransactionalPromiseDecorator createTransactionalPromiseDecorator(PlatformTransactionManager transactionManager, Method method) {
        if (method) {
            final Transactional txAnn = method.getAnnotation(Transactional)
            if (txAnn) {
                return new TransactionalPromiseDecorator(transactionManager, txAnn)
            }
        }
        return new TransactionalPromiseDecorator(transactionManager)
    }
}

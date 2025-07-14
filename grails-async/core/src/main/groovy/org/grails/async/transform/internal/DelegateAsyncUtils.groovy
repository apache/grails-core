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
package org.grails.async.transform.internal

import grails.async.decorator.PromiseDecorator
import grails.async.decorator.PromiseDecoratorProvider
import groovy.transform.CompileStatic

/**
 * Helps looking up the decorators
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class DelegateAsyncUtils {
    /**
     * Obtains all {@link PromiseDecorator} instances for the target and additional decorators supplied
     *
     * @param target The target
     * @param additional The additional
     * @return The additional promise decorators
     */
    static Collection<PromiseDecorator> getPromiseDecorators(Object target, Collection<PromiseDecorator> additional) {
        Collection<PromiseDecorator> decorators = []
        if (target instanceof PromiseDecoratorProvider) {
            decorators.addAll(((PromiseDecoratorProvider) target).getDecorators())
        }
        if (additional) {
            decorators.addAll(additional)
        }
        return decorators
    }
}

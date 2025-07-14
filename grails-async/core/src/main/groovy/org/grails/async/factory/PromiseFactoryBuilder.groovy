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
package org.grails.async.factory

import grails.async.PromiseFactory
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.async.factory.future.CachedThreadPoolPromiseFactory

/**
 * Constructs the default promise factory
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@CompileStatic
@Slf4j
class PromiseFactoryBuilder {

    /**
     * @return Builds the default PromiseFactory
     */
    static PromiseFactory build() {

        List<PromiseFactory> promiseFactories = ServiceLoader.load(PromiseFactory).toList()

        PromiseFactory promiseFactory
        if (promiseFactories.isEmpty()) {
            log.debug 'No PromiseFactory implementation found. Using default ExecutorService promise factory.'
            promiseFactory = new CachedThreadPoolPromiseFactory()
        } else {
            promiseFactory = promiseFactories.first()
            log.debug 'Found PromiseFactory implementation to use [{}]', promiseFactory
        }

        return promiseFactory
    }
}

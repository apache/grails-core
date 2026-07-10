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
package grails.async

import org.grails.async.factory.PromiseFactoryBuilder
import org.grails.async.factory.future.VirtualThreadPromiseFactory
import spock.lang.Specification

class VirtualThreadPromiseFactorySpec extends Specification {

    def cleanup() {
        System.clearProperty('grails.async.promiseFactory')
        Promises.promiseFactory = null
    }

    void 'builder can opt in to virtual thread promise factory'() {
        given:
        System.setProperty('grails.async.promiseFactory', 'virtual-thread')

        expect:
        PromiseFactoryBuilder.build() instanceof VirtualThreadPromiseFactory
    }

    void 'virtual thread factory executes promises'() {
        given:
        def factory = new VirtualThreadPromiseFactory()

        when:
        Promise<Integer> promise = factory.createPromise { 21 * 2 }

        then:
        promise.get() == 42

        cleanup:
        factory.close()
    }
}

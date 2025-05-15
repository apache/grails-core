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
package grails.async.services

import grails.async.Promises
import grails.async.web.WebPromises
import grails.util.GrailsWebMockUtil
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.PendingFeatureIf
import spock.lang.Specification

/**
 * Created by graemerocher on 20/02/2017.
 */
class WebPromisesSpec extends Specification {

    @PendingFeatureIf({
        // thrown() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void 'Test web promises handling'() {

        setup:
            GrailsWebMockUtil.bindMockWebRequest()

        when: 'A promise is created'
            def webPromise = WebPromises.task {
                RequestContextHolder.currentRequestAttributes()
            }
            webPromise.get() != null

        then: 'Async was requested'
            def e = thrown(IllegalStateException)
            e.message == 'The current request does not support Async processing'

        when: 'A normal promise is used'
            def promise = Promises.task { 'good' }

        then: 'No request is bound'
            promise.get() == 'good'

        cleanup:
            RequestContextHolder.setRequestAttributes(null)
    }
}

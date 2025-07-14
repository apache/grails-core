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
package org.grails.plugins.web.async

import grails.plugins.Plugin
import org.grails.plugins.web.async.mvc.AsyncActionResultTransformer
import org.grails.plugins.web.async.spring.PromiseFactoryBean

/**
 * Async support for the Grails 2.0. Doesn't do much right now, most logic handled
 * by the compile time transform.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
class ControllersAsyncGrailsPlugin extends Plugin {

    def grailsVersion = "7.0.0 > *"
    def loadAfter = ['controllers']

    Closure doWithSpring() {
        { ->
            asyncPromiseResponseActionResultTransformer(AsyncActionResultTransformer)
            grailsPromiseFactory(PromiseFactoryBean)
        }
    }
}

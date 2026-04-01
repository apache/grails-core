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
package micronaut

import groovy.transform.CompileStatic

import bean.injection.AppConfig
import bean.injection.FactoryCreatedService
import bean.injection.JavaSingletonService
import bean.injection.NamedService

import org.springframework.beans.factory.annotation.Autowired

import grails.converters.JSON

@CompileStatic
class MicronautTestController {

    @Autowired
    JavaSingletonService javaSingletonService

    @Autowired
    FactoryCreatedService factoryCreatedService

    @Autowired
    AppConfig appConfig

    @Autowired
    NamedService namedService

    def index() {
        render([
            javaMessage: javaSingletonService.message,
            factoryName: factoryCreatedService.name,
            appName: appConfig.name,
            namedService: namedService.name
        ] as JSON)
    }
}

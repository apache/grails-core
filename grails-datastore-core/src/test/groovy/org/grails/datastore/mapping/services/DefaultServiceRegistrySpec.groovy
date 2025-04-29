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
package org.grails.datastore.mapping.services

import org.grails.datastore.mapping.core.Datastore
import spock.lang.Specification

/**
 * Created by graemerocher on 11/01/2017.
 */
class DefaultServiceRegistrySpec extends Specification {

    void "test load services into service registry"() {
        given:
        ServiceRegistry reg = new DefaultServiceRegistry(Mock(Datastore))
        reg.initialize()
        ServiceRegistry reg2 = new DefaultServiceRegistry(Mock(Datastore))
        reg2.initialize()
        expect:
        reg.getService(TestService) != null
        reg.getService(TestService).datastore != null
        reg.getService(ITestService) != null
        reg.getService(ITestService).is reg.getService(TestService)
        reg.getService(TestService) != reg2.getService(TestService)
        reg.getService(TestService).datastore != reg2.getService(TestService).datastore
    }
}

class TestService implements Service, ITestService {
}
interface ITestService {}
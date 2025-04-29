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
package grails.test.mixin

import grails.artefact.Artefact
import org.grails.core.artefact.ServiceArtefactHandler
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Specification

/**
 * Created by graemerocher on 04/10/2016.
 */
class ServiceTestMixinInheritanceSpec extends TestSuperClass {

    void "Test inherit transaction manager"() {
        expect:
        transactionManager.is(TestSuperClass.manager)
    }
}
@Artefact(ServiceArtefactHandler.TYPE)
class TestService {

}
class TestSuperClass extends Specification {
    public static final DatastoreTransactionManager manager = new DatastoreTransactionManager()

    PlatformTransactionManager getTransactionManager() {
        return manager
    }
}

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
package org.grails.datastore.gorm.services

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.TransactionSystemException

import org.grails.datastore.mapping.core.Datastore
import spock.lang.Specification

/**
 * The diff only adds the explicit {@code datastore}/{@code getDatastore()}/{@code setDatastore()}
 * {@code Service} contract (previously implicit/inherited) - the rest of this class (the
 * {@code withTransaction}/{@code withRollback}/{@code withNewTransaction} overloads) predates this
 * PR and is already exercised via real {@code TransactionService} usage in the separate
 * {@code grails-datamapping-core-test} module (which doesn't count toward this module's own
 * coverage - see item 14's cross-module attribution note), so out of scope here.
 */
class DefaultTransactionServiceSpec extends Specification {

    void "getDatastore/setDatastore round-trip"() {
        given:
        def service = new DefaultTransactionService()
        def datastore = Mock(Datastore)

        when:
        service.setDatastore(datastore)

        then:
        service.getDatastore().is(datastore)
    }
}

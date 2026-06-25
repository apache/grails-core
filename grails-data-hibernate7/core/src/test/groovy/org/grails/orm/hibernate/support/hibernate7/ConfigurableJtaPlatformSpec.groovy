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
package org.grails.orm.hibernate.support.hibernate7

import jakarta.transaction.Transaction
import jakarta.transaction.TransactionManager
import jakarta.transaction.UserTransaction
import spock.lang.Specification

class ConfigurableJtaPlatformSpec extends Specification {

    def "test ConfigurableJtaPlatform registers synchronization"() {
        given: "A platform with mocked JTA components"
        def tm = Mock(TransactionManager)
        def ut = Mock(UserTransaction)
        def tx = Mock(Transaction)
        def platform = new ConfigurableJtaPlatform(tm, ut, null)
        def sync = Mock(jakarta.transaction.Synchronization)

        when: "registerSynchronization is called"
        platform.registerSynchronization(sync)

        then: "it correctly delegates to the transaction manager"
        1 * tm.getTransaction() >> tx
        1 * tx.registerSynchronization(sync)
    }
}

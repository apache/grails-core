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
package org.grails.datastore.gorm

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class ManyToManySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Account, Invoice])
    }

    void "Test save and load many-to-many association"() {
        given: "A many-to-many association"
        Account account = new Account().save()
        assert account

        account.addToInvoices(new Invoice())
        account.save(flush: true)
        manager.session.clear()

        when: "The association is loaded"
        account = Account.get(account.id)

        then: "The results are correct"
        account != null
        account.invoices.size() == 1
        account.invoices.iterator().next().accounts.size() == 1
    }
}

@Entity
class Account {
    Long id
    Set invoices
    static hasMany = [invoices: Invoice]
}

@Entity
class Invoice {
    Long id
    static belongsTo = [Account]
    Set accounts
    static hasMany = [accounts: Account]
}

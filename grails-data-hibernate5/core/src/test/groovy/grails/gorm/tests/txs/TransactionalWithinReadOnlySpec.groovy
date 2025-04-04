/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package grails.gorm.tests.txs

import grails.gorm.tests.services.Attribute
import grails.gorm.tests.services.Product
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import org.grails.orm.hibernate.GormSpec
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.interceptor.TransactionAspectSupport
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class TransactionalWithinReadOnlySpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Product, Attribute)


    void "test transaction status"() {
        given:
        TxService txService = new TxService()

        expect:
        txService.readProduct()
        !txService.writeProduct()
    }

}

@ReadOnly
class TxService {

    boolean readProduct() {
        def tx = transactionStatus
        tx.readOnly
    }

    @Transactional
    boolean writeProduct() {
        def tx = transactionStatus
        tx.readOnly
    }
}

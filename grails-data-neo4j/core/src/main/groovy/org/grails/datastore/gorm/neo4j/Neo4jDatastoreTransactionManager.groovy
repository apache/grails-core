/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the 'License'); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an 'AS IS' BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.datastore.gorm.neo4j

import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.TransactionException
import org.springframework.transaction.support.DefaultTransactionStatus

import org.grails.datastore.mapping.transactions.DatastoreTransactionManager
import org.grails.datastore.mapping.transactions.TransactionObject

/**
 * @author Stefan Armbruster
 * @author Graeme Rocher
 */

@CompileStatic
class Neo4jDatastoreTransactionManager extends DatastoreTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(Neo4jDatastoreTransactionManager)

    Neo4jDatastoreTransactionManager(Neo4jDatastore datastore) {
        setDatastore(datastore)
    }

    /**
     * Also marks the Neo4j transaction rollback-only, so that it rolls back rather than commits even
     * if its commit is reached, and leaves it open for the surrounding transaction's later writes.
     * @param status The transaction status
     * @throws TransactionException
     */
    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
        super.doSetRollbackOnly(status)
        TransactionObject txObject = (TransactionObject) status.getTransaction()
        Neo4jTransaction neo4jTransaction = (Neo4jTransaction) txObject.getTransaction()
        if (neo4jTransaction != null) {
            neo4jTransaction.rollbackOnly()
        }
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) throws TransactionException {
        TransactionObject txObject = (TransactionObject) transaction
        Neo4jTransaction tx = txObject == null ? null : (Neo4jTransaction) txObject.getTransaction()
        return tx != null && tx.isActive() && !tx.isSessionCreated()
    }

}

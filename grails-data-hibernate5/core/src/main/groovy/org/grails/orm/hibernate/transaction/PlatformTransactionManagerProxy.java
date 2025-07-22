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

package org.grails.orm.hibernate.transaction;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

/**
 * A proxy for the {@link org.springframework.transaction.PlatformTransactionManager} instance
 *
 * @author Graeme Rocher
 * @author Burt Beckwith
 */
public class PlatformTransactionManagerProxy implements PlatformTransactionManager {
    private PlatformTransactionManager targetTransactionManager;

    public PlatformTransactionManagerProxy() {

    }

    public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
        return targetTransactionManager.getTransaction(definition);
    }

    public void commit(TransactionStatus status) throws TransactionException {
        targetTransactionManager.commit(status);
    }

    public void rollback(TransactionStatus status) throws TransactionException {
        targetTransactionManager.rollback(status);
    }

    public PlatformTransactionManager getTargetTransactionManager() {
        return targetTransactionManager;
    }

    public void setTargetTransactionManager(PlatformTransactionManager targetTransactionManager) {
        this.targetTransactionManager = targetTransactionManager;
    }
}

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
package org.grails.transaction

import groovy.transform.CompileStatic
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionException
import org.springframework.transaction.TransactionStatus
import org.springframework.util.Assert

/**
 * {@link TransactionStatus} implementation to orchestrate {@link TransactionStatus} instances for multiple
 * {@link PlatformTransactionManager} instances.
 *
 * @author Michael Hunger
 * @author Oliver Gierke
 * @since 2.3.6
 */
@CompileStatic
class MultiTransactionStatus implements TransactionStatus {

    private final PlatformTransactionManager mainTransactionManager
    private final Map<PlatformTransactionManager, TransactionStatus> transactionStatuses = Collections
            .synchronizedMap(new HashMap<>())

    private boolean newSynchronization

    /**
     * Creates a new {@link MultiTransactionStatus} for the given {@link PlatformTransactionManager}.
     *
     * @param mainTransactionManager must not be {@literal null}.
     */
    MultiTransactionStatus(PlatformTransactionManager mainTransactionManager) {

        Assert.notNull(mainTransactionManager, 'TransactionManager must not be null!')
        this.mainTransactionManager = mainTransactionManager
    }

    Map<PlatformTransactionManager, TransactionStatus> getTransactionStatuses() {
        return transactionStatuses
    }

    void setNewSynchronization() {
        this.newSynchronization = true
    }

    boolean isNewSynchronization() {
        return newSynchronization
    }

    void registerTransactionManager(TransactionDefinition definition, PlatformTransactionManager transactionManager) {
        getTransactionStatuses().put(transactionManager, transactionManager.getTransaction(definition))
    }

    void commit(PlatformTransactionManager transactionManager) {
        TransactionStatus transactionStatus = getTransactionStatus(transactionManager)
        transactionManager.commit(transactionStatus)
    }

    /**
     * Rolls back the {@link TransactionStatus} registered for the given {@link PlatformTransactionManager}.
     *
     * @param transactionManager must not be {@literal null}.
     */
    void rollback(PlatformTransactionManager transactionManager) {
        transactionManager.rollback(getTransactionStatus(transactionManager))
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.transaction.TransactionStatus#isRollbackOnly()
     */
    boolean isRollbackOnly() {
        return getMainTransactionStatus().isRollbackOnly()
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.transaction.TransactionStatus#isCompleted()
     */
    boolean isCompleted() {
        return getMainTransactionStatus().isCompleted()
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.transaction.TransactionStatus#isNewTransaction()
     */
    boolean isNewTransaction() {
        return getMainTransactionStatus().isNewTransaction()
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.transaction.TransactionStatus#hasSavepoint()
     */
    boolean hasSavepoint() {
        return getMainTransactionStatus().hasSavepoint()
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.transaction.TransactionStatus#setRollbackOnly()
     */
    void setRollbackOnly() {
        for (TransactionStatus ts in transactionStatuses.values()) {
            ts.setRollbackOnly()
        }
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.transaction.SavepointManager#createSavepoint()
     */
    Object createSavepoint() throws TransactionException {

        SavePoints savePoints = new SavePoints()

        for (TransactionStatus transactionStatus in transactionStatuses.values()) {
            savePoints.save(transactionStatus)
        }
        return savePoints
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.transaction.SavepointManager#rollbackToSavepoint(java.lang.Object)
     */
    void rollbackToSavepoint(Object savepoint) throws TransactionException {
        SavePoints savePoints = (SavePoints) savepoint
        savePoints.rollback()
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.transaction.SavepointManager#releaseSavepoint(java.lang.Object)
     */
    void releaseSavepoint(Object savepoint) throws TransactionException {
        ((SavePoints) savepoint).release()
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.transaction.TransactionStatus#flush()
     */
    void flush() {
        for (TransactionStatus transactionStatus in transactionStatuses.values()) {
            transactionStatus.flush()
        }
    }

    private TransactionStatus getMainTransactionStatus() {
        return transactionStatuses.get(mainTransactionManager)
    }

    private TransactionStatus getTransactionStatus(PlatformTransactionManager transactionManager) {
        return this.getTransactionStatuses().get(transactionManager)
    }

    private static class SavePoints {

        private final Map<TransactionStatus, Object> savepoints = new HashMap<>()

        private void addSavePoint(TransactionStatus status, Object savepoint) {

            Assert.notNull(status, 'TransactionStatus must not be null!')
            this.savepoints.put(status, savepoint)
        }

        private void save(TransactionStatus transactionStatus) {
            Object savepoint = transactionStatus.createSavepoint()
            addSavePoint(transactionStatus, savepoint)
        }

        void rollback() {
            for (TransactionStatus transactionStatus in savepoints.keySet()) {
                transactionStatus.rollbackToSavepoint(savepointFor(transactionStatus))
            }
        }

        private Object savepointFor(TransactionStatus transactionStatus) {
            return savepoints.get(transactionStatus)
        }

        void release() {
            for (TransactionStatus transactionStatus in savepoints.keySet()) {
                transactionStatus.releaseSavepoint(savepointFor(transactionStatus))
            }
        }
    }

}

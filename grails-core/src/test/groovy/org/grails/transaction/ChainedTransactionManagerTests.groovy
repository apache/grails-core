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

import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.springframework.transaction.HeuristicCompletionException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionException
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.UnexpectedRollbackException
import org.springframework.transaction.support.DefaultTransactionDefinition

import static org.grails.transaction.ChainedTransactionManagerTests.TestPlatformTransactionManager.createFailingTransactionManager
import static org.grails.transaction.ChainedTransactionManagerTests.TestPlatformTransactionManager.createNonFailingTransactionManager
import static org.grails.transaction.ChainedTransactionManagerTests.TransactionManagerMatcher.isCommitted
import static org.grails.transaction.ChainedTransactionManagerTests.TransactionManagerMatcher.wasRolledback
import static org.hamcrest.CoreMatchers.is
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.fail
import static org.springframework.transaction.HeuristicCompletionException.getStateString

/**
 * Integration tests for {@link ChainedTransactionManager}.
 *
 * @author Michael Hunger
 * @author Oliver Gierke
 * @since 1.6
 */
class ChainedTransactionManagerTests {

    ChainedTransactionManager tm

    @Test
    void shouldCompleteSuccessfully() {

        PlatformTransactionManager transactionManager = createNonFailingTransactionManager('single')
        setupTransactionManagers(transactionManager)

        createAndCommitTransaction()

        assertThat(transactionManager, isCommitted())
    }

    @Test
    void shouldThrowRolledBackExceptionForSingleTMFailure() {

        setupTransactionManagers(createFailingTransactionManager('single'))

        try {
            createAndCommitTransaction()
            fail("Didn't throw the expected exception")
        } catch (HeuristicCompletionException e) {
            assertEquals(HeuristicCompletionException.STATE_ROLLED_BACK, e.getOutcomeState())
        }
    }

    @Test
    void shouldCommitAllRegisteredTransactionManagers() {

        PlatformTransactionManager first = createNonFailingTransactionManager('first')
        PlatformTransactionManager second = createNonFailingTransactionManager('second')

        setupTransactionManagers(first, second)
        createAndCommitTransaction()

        assertThat(first, isCommitted())
        assertThat(second, isCommitted())
    }

    @Test
    void shouldCommitInReverseOrder() {

        PlatformTransactionManager first = createNonFailingTransactionManager('first')
        PlatformTransactionManager second = createNonFailingTransactionManager('second')

        setupTransactionManagers(first, second)
        createAndCommitTransaction()

        assertThat('second tm commited before first ', commitTime(first) >= commitTime(second), is(true))
    }

    @Test
    void shouldDeduplicateRepeatedTransactionManagerInstances() {

        // The same PlatformTransactionManager instance can be supplied more than once when it is
        // exposed under multiple bean names (for example the primary data source manager aliased by
        // an additional transactionManager_<dataSource> bean). It must be registered - and therefore
        // committed - only once, otherwise the second commit fails with
        // "Transaction is already completed - do not call commit or rollback more than once".
        PlatformTransactionManager single = createNonFailingTransactionManager('single')

        setupTransactionManagers(single, single)

        assertThat(tm.getTransactionManagers().size(), is(1))

        createAndCommitTransaction()

        assertThat(single, isCommitted())
    }

    @Test
    void shouldThrowMixedRolledBackExceptionForNonFirstTMFailure() {

        setupTransactionManagers(TestPlatformTransactionManager.createFailingTransactionManager('first'),
                createNonFailingTransactionManager('second'))

        try {
            createAndCommitTransaction()
            fail("Didn't throw the expected exception")
        } catch (HeuristicCompletionException e) {
            assertHeuristicException(HeuristicCompletionException.STATE_MIXED, e.getOutcomeState())
        }
    }

    @Test
    void shouldRollbackAllTransactionManagers() {

        PlatformTransactionManager first = createNonFailingTransactionManager('first')
        PlatformTransactionManager second = createNonFailingTransactionManager('second')

        setupTransactionManagers(first, second)
        createAndRollbackTransaction()

        assertThat(first, wasRolledback())
        assertThat(second, wasRolledback())
    }

    @Test
    void shouldThrowExceptionOnFailingRollback() {
        assertThrows(UnexpectedRollbackException, { ->
            PlatformTransactionManager first = createFailingTransactionManager('first')
            setupTransactionManagers(first)
            createAndRollbackTransaction()
        } as Executable)
    }

    private void setupTransactionManagers(PlatformTransactionManager... transactionManagers) {
        tm = new ChainedTransactionManager(new TestSynchronizationManager(), transactionManagers)
    }

    private void createAndRollbackTransaction() {
        MultiTransactionStatus transaction = tm.getTransaction(new DefaultTransactionDefinition())
        tm.rollback(transaction)
    }

    private void createAndCommitTransaction() {
        MultiTransactionStatus transaction = tm.getTransaction(new DefaultTransactionDefinition())
        tm.commit(transaction)
    }

    private static void assertHeuristicException(int expected, int actual) {
        assertThat(getStateString(actual), is(getStateString(expected)))
    }

    private static Long commitTime(PlatformTransactionManager transactionManager) {
        return ((TestPlatformTransactionManager) transactionManager).getCommitTime()
    }

    static class TestSynchronizationManager implements SynchronizationManager {

        private boolean synchronizationActive

        void initSynchronization() {
            synchronizationActive = true
        }

        boolean isSynchronizationActive() {
            return synchronizationActive
        }

        void clearSynchronization() {
            synchronizationActive = false
        }
    }

    static class TestPlatformTransactionManager implements PlatformTransactionManager {

        private final String name
        private Long commitTime
        private Long rollbackTime

        TestPlatformTransactionManager(String name) {
            this.name = name
        }

        static PlatformTransactionManager createFailingTransactionManager(String name) {
            return new TestPlatformTransactionManager(name + '-failing') {
                @Override
                void commit(TransactionStatus status) throws TransactionException {
                    throw new RuntimeException()
                }

                @Override
                void rollback(TransactionStatus status) throws TransactionException {
                    throw new RuntimeException()
                }
            }
        }

        static PlatformTransactionManager createNonFailingTransactionManager(String name) {
            return new TestPlatformTransactionManager(name + '-non-failing')
        }

        @Override
        String toString() {
            return name + (isCommitted() ? ' (committed) ' : ' (not committed)')
        }

        TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            return new TestTransactionStatus(definition)
        }

        void commit(TransactionStatus status) throws TransactionException {
            commitTime = System.currentTimeMillis()
        }

        void rollback(TransactionStatus status) throws TransactionException {
            rollbackTime = System.currentTimeMillis()
        }

        boolean isCommitted() {
            return commitTime != null
        }

        boolean wasRolledBack() {
            return rollbackTime != null
        }

        Long getCommitTime() {
            return commitTime
        }

        static class TestTransactionStatus implements TransactionStatus {

            TestTransactionStatus(TransactionDefinition definition) {
            }

            boolean isNewTransaction() {
                return false
            }

            boolean hasSavepoint() {
                return false
            }

            void setRollbackOnly() {
            }

            boolean isRollbackOnly() {
                return false
            }

            void flush() {
            }

            boolean isCompleted() {
                return false
            }

            Object createSavepoint() throws TransactionException {
                return null
            }

            void rollbackToSavepoint(Object savepoint) throws TransactionException {
            }

            void releaseSavepoint(Object savepoint) throws TransactionException {
            }
        }

        String getName() {
            return name
        }
    }

    static class TransactionManagerMatcher extends TypeSafeMatcher<PlatformTransactionManager> {

        private final boolean commitCheck

        TransactionManagerMatcher(boolean commitCheck) {
            this.commitCheck = commitCheck
        }

        @Override
        boolean matchesSafely(PlatformTransactionManager platformTransactionManager) {
            TestPlatformTransactionManager ptm = (TestPlatformTransactionManager) platformTransactionManager
            if (commitCheck) {
                return ptm.isCommitted()
            } else {
                return ptm.wasRolledBack()
            }
        }

        void describeTo(Description description) {
            description.appendText('that a ' + (commitCheck ? 'committed' : 'rolled-back') + ' TransactionManager')
        }

        static TransactionManagerMatcher isCommitted() {
            return new TransactionManagerMatcher(true)
        }

        static TransactionManagerMatcher wasRolledback() {
            return new TransactionManagerMatcher(false)
        }
    }
}

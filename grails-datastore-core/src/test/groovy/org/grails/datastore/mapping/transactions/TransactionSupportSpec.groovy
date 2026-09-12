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
package org.grails.datastore.mapping.transactions

import org.springframework.transaction.NoTransactionException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.DefaultTransactionAttribute
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute
import org.springframework.transaction.interceptor.RollbackRuleAttribute
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.transactions.support.SpringSessionSynchronization

class TransactionSupportSpec extends Specification {

    Datastore datastore = Mock(Datastore)
    Session session = Mock(Session)
    Transaction transaction = Mock(Transaction)

    void cleanup() {
        if (TransactionSynchronizationManager.hasResource(datastore)) {
            TransactionSynchronizationManager.unbindResource(datastore)
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    void "session holder tracks sessions in binding order"() {
        given:
        Session second = Mock(Session)
        SessionHolder holder = new SessionHolder(session, 'creator')

        expect:
        holder.creator == 'creator'
        holder.session.is(session)
        holder.sessions.toList() == [session]
        !holder.empty
        !holder.doesNotHoldNonDefaultSession()
        holder.size() == 1
        holder.containsSession(session)
        new SessionHolder(session).creator == null

        when:
        holder.addSession(second)

        then:
        holder.session.is(second)
        holder.sessions.toList() == [session, second]
        holder.size() == 2

        when:
        holder.sessions.clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        holder.removeSession(second)

        then:
        holder.session.is(session)
        !holder.containsSession(second)

        when:
        holder.removeSession(session)

        then:
        holder.empty
        holder.doesNotHoldNonDefaultSession()
        holder.session == null
        holder.transaction == null
    }

    void "session holder exposes the active transaction and validates connections"() {
        given:
        SessionHolder holder = new SessionHolder(session)

        when:
        Transaction tx = holder.transaction

        then:
        1 * session.hasTransaction() >> true
        1 * session.getTransaction() >> transaction
        tx.is(transaction)

        when:
        tx = holder.transaction

        then:
        1 * session.hasTransaction() >> false
        0 * session.getTransaction()
        tx == null

        when:
        holder.setSynchronizedWithTransaction(true)

        then:
        1 * session.setSynchronizedWithTransaction(true)
        holder.synchronizedWithTransaction

        when:
        Session validated = holder.validatedSession

        then:
        1 * session.isConnected() >> true
        validated.is(session)
        holder.size() == 1

        when:
        validated = holder.validatedSession

        then:
        1 * session.isConnected() >> false
        validated == null
        holder.empty

        when:
        holder.setTransaction(transaction)

        then:
        noExceptionThrown()
    }

    void "session only transactions flush on commit and clear on rollback exactly once"() {
        given:
        SessionOnlyTransaction tx = new SessionOnlyTransaction('native', session)

        expect:
        tx.nativeTransaction == 'native'
        tx.active

        when:
        tx.setTimeout(10)
        tx.commit()
        tx.commit()

        then:
        1 * session.flush()
        !tx.active

        when:
        tx.rollback()

        then:
        0 * session.clear()

        when:
        SessionOnlyTransaction other = new SessionOnlyTransaction(null, session)
        other.rollback()
        other.rollback()

        then:
        1 * session.clear()
        !other.active
    }

    void "session only transactions deactivate even when the session fails"() {
        given:
        SessionOnlyTransaction tx = new SessionOnlyTransaction(null, session)
        session.flush() >> { throw new IllegalStateException('boom') }

        when:
        tx.commit()

        then:
        thrown(IllegalStateException)
        !tx.active
    }

    void "transaction objects describe how their session holder was obtained"() {
        given:
        TransactionObject txObject = new TransactionObject()
        SessionHolder holder = new SessionHolder(session)

        when:
        txObject.setSession(session)

        then:
        txObject.newSession
        txObject.newSessionHolder
        txObject.sessionHolder.session.is(session)

        when:
        txObject.setExistingSession(session)

        then:
        !txObject.newSession
        txObject.newSessionHolder

        when:
        txObject.setSessionHolder(holder)

        then:
        !txObject.newSession
        !txObject.newSessionHolder
        txObject.sessionHolder.is(holder)
        !txObject.rollbackOnly

        when:
        holder.setRollbackOnly()
        txObject.setTransaction(transaction)
        txObject.flush()
        Transaction tx = txObject.transaction

        then:
        txObject.rollbackOnly
        1 * session.flush()
        1 * session.hasTransaction() >> true
        1 * session.getTransaction() >> transaction
        tx.is(transaction)
    }

    void "transaction utils look up the thread bound transaction"() {
        given:
        session.hasTransaction() >> true
        session.getTransaction() >> transaction

        expect:
        !TransactionUtils.isTransactionPresent(datastore)
        TransactionUtils.getTransaction(datastore) == null

        when:
        TransactionUtils.currentTransaction(datastore)

        then:
        NoTransactionException e = thrown()
        e.message == 'No transaction started.'

        when:
        TransactionSynchronizationManager.bindResource(datastore, new SessionHolder(session))

        then:
        TransactionUtils.isTransactionPresent(datastore)
        TransactionUtils.getTransaction(datastore).is(transaction)
        TransactionUtils.currentTransaction(datastore).is(transaction)
    }

    void "customizable rollback attributes roll back on every exception unless a no-rollback rule wins"() {
        given:
        CustomizableRollbackTransactionAttribute attribute = new CustomizableRollbackTransactionAttribute()

        expect:
        attribute.inheritRollbackOnly
        attribute.connection == null
        attribute.rollbackOn(new IOException('checked'))
        attribute.rollbackOn(new IllegalStateException('unchecked'))

        when:
        attribute.rollbackRules = [new NoRollbackRuleAttribute(IOException)]
        attribute.inheritRollbackOnly = false
        attribute.connection = 'secondary'

        then:
        !attribute.rollbackOn(new IOException('checked'))
        !attribute.rollbackOn(new FileNotFoundException('subclass'))
        attribute.rollbackOn(new IllegalStateException('unchecked'))
        !attribute.inheritRollbackOnly
        attribute.connection == 'secondary'

        when: 'the deepest matching rule wins'
        attribute.rollbackRules = [new NoRollbackRuleAttribute(IOException), new RollbackRuleAttribute(FileNotFoundException)]

        then:
        attribute.rollbackOn(new FileNotFoundException('subclass'))
        !attribute.rollbackOn(new IOException('checked'))
    }

    void "customizable rollback attributes copy their settings from other definitions"() {
        given:
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
        definition.isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
        definition.timeout = 30
        definition.readOnly = true
        definition.name = 'def'
        DefaultTransactionAttribute transactionAttribute = new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_MANDATORY)
        transactionAttribute.timeout = 5
        transactionAttribute.name = 'attr'

        when:
        CustomizableRollbackTransactionAttribute fromDefinition = new CustomizableRollbackTransactionAttribute(definition)
        CustomizableRollbackTransactionAttribute fromAttribute =
                new CustomizableRollbackTransactionAttribute((org.springframework.transaction.interceptor.TransactionAttribute) transactionAttribute)
        CustomizableRollbackTransactionAttribute withRules = new CustomizableRollbackTransactionAttribute(
                TransactionDefinition.PROPAGATION_NESTED, [new NoRollbackRuleAttribute(IOException)])

        then:
        fromDefinition.propagationBehavior == TransactionDefinition.PROPAGATION_REQUIRES_NEW
        fromDefinition.isolationLevel == TransactionDefinition.ISOLATION_SERIALIZABLE
        fromDefinition.timeout == 30
        fromDefinition.readOnly
        fromDefinition.name == 'def'
        fromAttribute.propagationBehavior == TransactionDefinition.PROPAGATION_MANDATORY
        fromAttribute.timeout == 5
        fromAttribute.name == 'attr'
        withRules.propagationBehavior == TransactionDefinition.PROPAGATION_NESTED
        !withRules.rollbackOn(new IOException('x'))

        when:
        withRules.inheritRollbackOnly = false
        CustomizableRollbackTransactionAttribute copy = new CustomizableRollbackTransactionAttribute(withRules)
        CustomizableRollbackTransactionAttribute fromRuleBased =
                new CustomizableRollbackTransactionAttribute(new RuleBasedTransactionAttribute())

        then:
        !copy.inheritRollbackOnly
        fromRuleBased.inheritRollbackOnly
    }

    void "spring session synchronization binds and closes the session for new sessions"() {
        given:
        SessionHolder holder = new SessionHolder(session)
        holder.setSynchronizedWithTransaction(true)
        SpringSessionSynchronization sync = new SpringSessionSynchronization(holder, datastore, true)
        TransactionSynchronizationManager.bindResource(datastore, holder)

        when:
        sync.suspend()

        then:
        1 * session.disconnect()
        !TransactionSynchronizationManager.hasResource(datastore)

        when:
        sync.resume()

        then:
        TransactionSynchronizationManager.getResource(datastore).is(holder)

        when:
        sync.flush()
        sync.beforeCommit(false)
        sync.afterCommit()
        sync.beforeCompletion()

        then:
        !TransactionSynchronizationManager.hasResource(datastore)

        when: 'the holder is no longer active so suspend and resume do nothing'
        sync.suspend()
        sync.resume()

        then:
        0 * session.disconnect()
        !TransactionSynchronizationManager.hasResource(datastore)

        when:
        sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)

        then:
        1 * session.disconnect()
        0 * session.setSynchronizedWithTransaction(_)
        holder.synchronizedWithTransaction

        when: 'a holder that no longer holds a session is unsynchronized'
        holder.removeSession(session)
        sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)

        then:
        0 * session.disconnect()
        !holder.synchronizedWithTransaction
    }

    void "spring session synchronization only disconnects pre-bound sessions"() {
        given:
        Session second = Mock(Session)
        SessionHolder holder = new SessionHolder(session)
        holder.addSession(second)
        SpringSessionSynchronization sync = new SpringSessionSynchronization(holder, datastore, false)
        TransactionSynchronizationManager.bindResource(datastore, holder)

        when:
        sync.beforeCompletion()

        then:
        TransactionSynchronizationManager.hasResource(datastore)

        when:
        sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)

        then:
        1 * second.disconnect()
        0 * session.disconnect()
        0 * second.setSynchronizedWithTransaction(_)
        0 * session.setSynchronizedWithTransaction(_)
    }

}

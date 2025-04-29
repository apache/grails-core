package org.grails.datastore.gorm.neo4j;

import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager;
import org.grails.datastore.mapping.transactions.Transaction;
import org.grails.datastore.mapping.transactions.TransactionObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.FlushModeType;
import java.io.IOException;

/**
 * @author Stefan Armbruster
 * @author Graeme Rocher
 */

public class Neo4jDatastoreTransactionManager extends DatastoreTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(Neo4jDatastoreTransactionManager.class);

    public Neo4jDatastoreTransactionManager(Neo4jDatastore datastore) {
        setDatastore(datastore);
    }

    /**
     * Override doSetRollbackOnly to call {@link org.neo4j.graphdb.Transaction#terminate()}
     * @param status The transaction status
     * @throws TransactionException
     */
    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
        super.doSetRollbackOnly(status);
        TransactionObject txObject = (TransactionObject) status.getTransaction();
        Neo4jTransaction neo4jTransaction = (Neo4jTransaction) txObject.getTransaction();
        if(neo4jTransaction != null) {
            neo4jTransaction.rollbackOnly();
        }
    }

    /**
     * same as superclass but passing through {@link org.springframework.transaction.TransactionDefinition}
     * to session.beginTransaction
     * @param o
     * @param definition
     * @throws TransactionException
     */
    @Override
    protected void doBegin(Object o, TransactionDefinition definition) throws TransactionException {
        TransactionObject txObject = (TransactionObject) o;

        Neo4jSession session = null;
        try {
            session = (Neo4jSession) txObject.getSessionHolder().getSession();

            if (definition.isReadOnly()) {
                // Just set to NEVER in case of a new Session for this transaction.
                session.setFlushMode(FlushModeType.COMMIT);
            }

            Transaction<?> tx = session.beginTransaction(definition);
            // Register transaction timeout.
            int timeout = determineTimeout(definition);
            if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
                tx.setTimeout(timeout);
            }

            // Bind the session holder to the thread.
            if (txObject.isNewSessionHolder()) {
                TransactionSynchronizationManager.bindResource(getDatastore(), txObject.getSessionHolder());
            }
            txObject.getSessionHolder().setSynchronizedWithTransaction(true);
            session.setSynchronizedWithTransaction(true);
        } catch (Exception ex) {
            if (txObject.isNewSession()) {
                try {
                    if (session != null && session.getTransaction().isActive()) {
                        session.getTransaction().rollback();
                    }
                } catch (Throwable ex2) {
                    logger.debug("Could not rollback Session after failed transaction begin", ex);
                } finally {
                    DatastoreUtils.closeSession(session);
                }
            }
            throw new CannotCreateTransactionException("Could not open Datastore Session for transaction", ex);
        }
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) throws TransactionException {
        TransactionObject txObject = (TransactionObject) transaction;
        Neo4jTransaction tx = txObject == null ? null : (Neo4jTransaction) txObject.getTransaction();
        return tx != null && tx.isActive() && !tx.isSessionCreated();
    }
}

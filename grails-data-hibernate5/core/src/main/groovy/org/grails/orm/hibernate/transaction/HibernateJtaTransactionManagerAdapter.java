package org.grails.orm.hibernate.transaction;

import jakarta.transaction.*;
import javax.transaction.xa.XAResource;

import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Adapter for adding transaction controlling hooks for supporting
 * Hibernate's org.hibernate.engine.transaction.Isolater class's interaction with transactions
 * 
 * This is required when there is no real JTA transaction manager in use and Spring's
 * {@link TransactionAwareDataSourceProxy} is used.
 * 
 * Without this solution, using Hibernate's TableGenerator identity strategies will fail to support transactions.
 * The id generator will commit the current transaction and break transactional behaviour.
 * 
 * The javadoc of Hibernate's {@code TableHiLoGenerator} states this. However this isn't mentioned in the javadocs of other TableGenerators.
 * 
 * @author Lari Hotari
 */
public class HibernateJtaTransactionManagerAdapter implements TransactionManager {
    PlatformTransactionManager springTransactionManager;
    ThreadLocal<TransactionStatus> currentTransactionHolder= new ThreadLocal<>();
    
    public HibernateJtaTransactionManagerAdapter(PlatformTransactionManager springTransactionManager) {
        this.springTransactionManager = springTransactionManager;
    }
    
    @Override
    public void begin() {
        TransactionDefinition definition=new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        currentTransactionHolder.set(springTransactionManager.getTransaction(definition));
    }

    @Override
    public void commit() throws
            SecurityException, IllegalStateException {
        springTransactionManager.commit(getAndRemoveStatus());
    }

    @Override
    public void rollback() throws IllegalStateException, SecurityException {
        springTransactionManager.rollback(getAndRemoveStatus());
    }
    
    @Override
    public void setRollbackOnly() throws IllegalStateException {
        currentTransactionHolder.get().setRollbackOnly();
    }
    
    protected TransactionStatus getAndRemoveStatus() {
        TransactionStatus status=currentTransactionHolder.get();
        currentTransactionHolder.remove();
        return status;
    }

    @Override
    public int getStatus() {
        TransactionStatus status=currentTransactionHolder.get();
        return convertToJtaStatus(status);
    }

    protected static int convertToJtaStatus(TransactionStatus status) {
        if (status != null) {
            if (status.isCompleted()) {
                return Status.STATUS_UNKNOWN;
            } else if (status.isRollbackOnly()) {
                return Status.STATUS_MARKED_ROLLBACK;
            } else {
                return Status.STATUS_ACTIVE;
            }
        } else { 
            return Status.STATUS_NO_TRANSACTION;
        }
    }

    @Override
    public Transaction getTransaction() {
        return new TransactionAdapter(springTransactionManager, currentTransactionHolder);
    }

    @Override
    public void resume(Transaction tobj) throws IllegalStateException {
        TransactionAdapter transaction = (TransactionAdapter)tobj;
        // commit the PROPAGATION_NOT_SUPPORTED transaction returned in suspend
        springTransactionManager.commit(transaction.transactionStatus);
    }

    @Override
    public Transaction suspend() {
        currentTransactionHolder.set(springTransactionManager.getTransaction(new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NOT_SUPPORTED)));
        return new TransactionAdapter(springTransactionManager, currentTransactionHolder);
    }
    
    @Override
    public void setTransactionTimeout(int seconds) {
        
    }

    private static class TransactionAdapter implements Transaction {
        PlatformTransactionManager springTransactionManager;
        TransactionStatus transactionStatus;
        ThreadLocal<TransactionStatus> currentTransactionHolder;
        
        TransactionAdapter(PlatformTransactionManager springTransactionManager, ThreadLocal<TransactionStatus> currentTransactionHolder) {
            this.springTransactionManager = springTransactionManager;
            this.currentTransactionHolder = currentTransactionHolder;
            this.transactionStatus = currentTransactionHolder.get();
        }
        
        @Override
        public void commit() throws
                SecurityException, IllegalStateException {
            springTransactionManager.commit(transactionStatus);
            currentTransactionHolder.remove();
        }

        @Override
        public boolean delistResource(XAResource xaRes, int flag) throws IllegalStateException, SystemException {
            return false;
        }

        @Override
        public boolean enlistResource(XAResource xaRes) throws RollbackException, IllegalStateException,
                SystemException {
            return false;
        }

        @Override
        public int getStatus() throws SystemException {
            return convertToJtaStatus(transactionStatus);
        }

        @Override
        public void registerSynchronization(final Synchronization sync) throws RollbackException, IllegalStateException,
                SystemException {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCompletion() {
                    sync.beforeCompletion();
                }
                
                @Override
                public void afterCompletion(int status) {
                    int jtaStatus;
                    if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        jtaStatus = Status.STATUS_COMMITTED;
                    } else if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        jtaStatus = Status.STATUS_ROLLEDBACK;
                    } else {
                        jtaStatus = Status.STATUS_UNKNOWN;
                    }
                    sync.afterCompletion(jtaStatus);
                }
                
                public void suspend() { }
                public void resume() { }
                public void flush() { }
                public void beforeCommit(boolean readOnly) { }
                public void afterCommit() { }
            });
        }

        @Override
        public void rollback() throws IllegalStateException, SystemException {
            springTransactionManager.rollback(transactionStatus);
            currentTransactionHolder.remove();            
        }

        @Override
        public void setRollbackOnly() throws IllegalStateException, SystemException {
            transactionStatus.setRollbackOnly();
        }
        
        @Override
        public boolean equals(Object obj) {
            if(obj == this) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (obj.getClass() == TransactionAdapter.class) {
                TransactionAdapter other = (TransactionAdapter)obj;
                if(other.transactionStatus == this.transactionStatus) {
                    return true;
                } else if (other.transactionStatus != null) {
                    return other.transactionStatus.equals(this.transactionStatus);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
        
        @Override
        public int hashCode() {
            return transactionStatus != null ? transactionStatus.hashCode() : System.identityHashCode(this);
        }
    }
}

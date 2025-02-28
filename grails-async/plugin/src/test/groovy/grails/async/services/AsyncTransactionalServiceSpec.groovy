package grails.async.services

import grails.async.DelegateAsync
import grails.transaction.TransactionManagerAware
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionException
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.SimpleTransactionStatus
import spock.lang.Specification

class AsyncTransactionalServiceSpec extends Specification {

    void 'Test that an async transactional service is transaction manager aware'() {

        when: 'a transactional service is used as a delegate'
            def asyncService = new AsyncRegularService()

        then: 'the async service is transactionManager aware'
            asyncService instanceof TransactionManagerAware

        when: 'the transaction manager is set'
            TransactionStatus txStatus
            TransactionDefinition txDef
            final txManager = new PlatformTransactionManager() {
                @Override
                TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
                    txDef = definition
                    txStatus = new SimpleTransactionStatus(true)
                    return txStatus
                }

                @Override
                void commit(TransactionStatus status) throws TransactionException {}

                @Override
                void rollback(TransactionStatus status) throws TransactionException {
                    status.setRollbackOnly()
                }
            }
            asyncService.transactionManager = txManager

        and: 'the async service is invoked'
            def result = asyncService.doWork().get()

        then: 'created promises are transactional'
            txStatus != null
            !txDef.readOnly

        when: 'custom transaction attributes are used'
            result = asyncService.readStuff().get()

        then: 'The custom tx attributes are used'
            txDef != null
            txDef.readOnly

    }
}

class RegularService {

    static transactional = true
    void doWork(String arg) {}

    @Transactional(readOnly = true)
    void readStuff(String arg) {}
}

class AsyncRegularService {

    @DelegateAsync RegularService regularService = new RegularService()
}

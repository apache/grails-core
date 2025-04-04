package grails.gorm.tests

import grails.gorm.transactions.TransactionService
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.springframework.transaction.TransactionStatus
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 11/01/2017.
 */
class TransactionServiceSpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore()

    void "test use transaction service"() {
        when:"the tx service is retrieved"
        TransactionService txService = datastore.getService(TransactionService)
        TransactionStatus status = txService.withTransaction { TransactionStatus status -> status }

        then:"The transaction status is correct"
        status.completed
        status.isNewTransaction()
        !status.isRollbackOnly()

        when:"rollback is used"
        status = txService.withRollback { TransactionStatus ts ->
            ts
        }

        then:"The transaction was rolled back"
        status.completed
        status.isRollbackOnly()
    }
}

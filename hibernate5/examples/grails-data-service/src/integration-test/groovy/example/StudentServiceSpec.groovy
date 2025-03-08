package example

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
@Rollback
class StudentServiceSpec extends Specification {

    StudentService studentService

    void "test regular service autowire by type in a Data Service"() {
        expect:
        studentService.testServiceBean != null
        studentService.testServiceBean.libraryService != null

    }

    void "test TenantService and TransactionService are not null"() {
        expect:
        studentService.transactionService != null
        studentService.tenantService != null
    }
}

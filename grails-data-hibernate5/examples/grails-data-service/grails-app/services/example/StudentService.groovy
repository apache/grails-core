package example

import grails.gorm.multitenancy.TenantService
import grails.gorm.services.Service
import grails.gorm.transactions.TransactionService
import grails.gorm.transactions.Transactional
import org.springframework.beans.factory.annotation.Autowired

@Service(Student)
abstract class StudentService {

    @Autowired
    TransactionService transactionService

    @Autowired
    TenantService tenantService

    @Autowired
    TestService testServiceBean

    abstract Student get(Serializable id)

    @Transactional
    List<Book> booksAllocated(Serializable studentId) {
        assert  testServiceBean != null
    }
}

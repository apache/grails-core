package grails.gorm.tests

import grails.gorm.tck.Person
import grails.gorm.transactions.Transactional

/**
 * Created by graemerocher on 05/01/2017.
 */
class TransactionalTransformOnServiceSpec extends GormDatastoreSpec {

    void "test transaction manager lookup with @Transactional and unassigned transaction manager"() {
        expect:
        new TestService().testMe()
        new ChildService().doSomething("value") == "parent value"
    }

    @Override
    List getDomainClasses() {
        [Person]
    }


}
@Transactional
class TestService {

    boolean testMe() {
        return transactionStatus != null
    }
}
abstract class ParentService {

    @Transactional
    String doSomething(String arg) {
        "parent $arg"
    }
}
class ChildService extends ParentService {


    @Transactional
    String doSomething(String arg) {
        super.doSomething(arg)
    }
}
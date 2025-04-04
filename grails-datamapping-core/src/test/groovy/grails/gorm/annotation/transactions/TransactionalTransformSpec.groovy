package grails.gorm.annotation.transactions

import grails.gorm.transactions.NotTransactional
import grails.gorm.transactions.Transactional
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.UnexpectedRollbackException
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.util.ReflectionUtils
import spock.lang.Issue
import spock.lang.Specification

import javax.sql.DataSource
/**
 */
class TransactionalTransformSpec extends Specification {

    void "test child method that calls super"() {
        when:"A service uses a generic argument"
        def (parent, child) = new GroovyShell().evaluate('''
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic

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

[ParentService, ChildService]
''')
        then:
        parent.getMethod("doSomething", String).getAnnotation(Transactional)
        child.getMethod("doSomething", String).getAnnotation(Transactional)
    }

    void "test transactional transform with generics"() {
        when:"A service uses a generic argument"
        def (testService, interfaceType) = new GroovyShell().evaluate('''
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic

@CompileStatic
@Transactional
class FooService<Param extends Named> {

    void serviceMethod(Param param) {

    }
}
interface Named {

    String getName()
}

[FooService, Named]
''')
        then:"the types are correct"
        interfaceType.name == "Named"
        testService.getMethod('serviceMethod', interfaceType) != null
    }

    void "Test transactional transform set target datastore method"() {
        when: "A subclass subclasses a transactional service"
        Class testService = new GroovyShell().evaluate('''
import grails.gorm.transactions.Transactional

    @Transactional
    class TestService {

        def foo() {
            "unknown"
        }
    }


    TestService
    ''')

        def field = ReflectionUtils.findField(testService, '$transactionManager')

        then: "It implements TransactionManagerAware"
        field.declaringClass.name == 'TestService'

        when:
        def instance = testService.newInstance()
        def mockDatastore = Mock(TransactionCapableDatastore)
        instance.targetDatastore = mockDatastore


        then:
        instance.targetDatastore == mockDatastore
    }

    @Issue('https://github.com/grails/grails-core/issues/9989')
    void "Test transactional transform when applied to inheritance"() {
        when: "A subclass subclasses a transactional service"
        Class dogService = new GroovyShell().evaluate('''
import grails.gorm.transactions.Transactional

    @Transactional
    class MammalService {

        def sound() {
            "unknown"
        }
    }

    @Transactional
    class DogService extends MammalService {

        @Override
        def sound() {
            "bark"
        }

    }

    DogService
    ''')

        def field = ReflectionUtils.findField(dogService, '$transactionManager')

        then: "It implements TransactionManagerAware"
        field.declaringClass.name == 'MammalService'

        when:
        dogService.newInstance().sound()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No GORM implementations configured. Ensure GORM has been initialized correctly'

    }

    @Issue('https://github.com/grails/grails-core/issues/9837')
    void "Test @Rollback when applied to Spock specifications with closures combined with where queries"() {
        when: "A new instance of a class with a @Transactional method is created that subclasses another transactional class"
        Class mySpec = new GroovyShell().evaluate('''
    import grails.gorm.transactions.*
    import spock.lang.Specification

    @Rollback
    class MySpec extends Specification {
        void 'test something'() {
            expect:
            def someClosure = { println x }
            x + y == sum

            where:
            x | y | sum
            4 | 2 | 6
        }
    }
    MySpec
    ''')

        then: "It implements TransactionManagerAware"
        mySpec.getDeclaredMethod('$spock_feature_0_0', Object, Object, Object)
        mySpec.getDeclaredMethod('$tt__$spock_feature_0_0', Object, Object, Object, TransactionStatus)

        and:"The spec can be called"
        mySpec.newInstance().'$tt__$spock_feature_0_0'(2,2,4,new DefaultTransactionStatus(new Object(), true, true, false, false, null))


    }

    @Issue('https://github.com/grails/grails-core/issues/9646')
    void "Test @Rollback when applied to Spock specifications with closures in then blocks"() {
        when: "A new instance of a class with a @Transactional method is created that subclasses another transactional class"
        Class mySpec = new GroovyShell().evaluate('''
    import grails.gorm.transactions.*
    import spock.lang.Specification

    @Rollback
    class MySpec extends Specification {
        void "my test method"() {
            when:
            List a = [1, 2, 3]

            then:
            transactionStatus != null
            transactionStatus.hasTransaction()
            a.each {
                assert it < 4
            }
        }
    }
    MySpec
    ''')

        then: "It implements TransactionManagerAware"
        mySpec.getDeclaredMethod('$spock_feature_0_0')
        mySpec.getDeclaredMethod('$tt__$spock_feature_0_0', TransactionStatus)

        and:"The spec can be called"
        mySpec.newInstance().'$tt__$spock_feature_0_0'(new DefaultTransactionStatus(new Object(), true, true, false, false, null))


    }

    void "Test @Rollback when applied to JUnit specifications"() {
        when:
        Class mySpec = new GroovyShell().evaluate('''
    import grails.gorm.transactions.*
    import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.BeforeEach
    import org.junit.jupiter.api.AfterEach

    @Rollback
    class MyJunitTest {
        @BeforeEach
        def junitSetup() {

        }

        @AfterEach
        def junitCleanup() {

        }

        @Test
        void junitTest() {
            expect:
                1 == 1
        }
    }
    MyJunitTest
    ''')

        then: "It implements TransactionManagerAware"
        mySpec.getDeclaredMethod('junitSetup')
        mySpec.getDeclaredMethod('$tt__junitSetup', TransactionStatus)
        mySpec.getDeclaredMethod('junitCleanup')
        mySpec.getDeclaredMethod('$tt__junitCleanup', TransactionStatus)

        mySpec.getDeclaredMethod('junitTest')
        mySpec.getDeclaredMethod('$tt__junitTest', TransactionStatus)
    }

    void "Test @Rollback when applied to Spock specifications"() {
        when: "A new instance of a class with a @Transactional method is created that subclasses another transactional class"
        Class mySpec = new GroovyShell().evaluate('''
    import grails.gorm.transactions.*
    import org.springframework.transaction.PlatformTransactionManager
    import org.springframework.transaction.TransactionStatus
    import org.springframework.transaction.annotation.Isolation
    import org.springframework.transaction.annotation.Propagation
    import spock.lang.Specification

    @Rollback
    class MySpec extends Specification {
        def setup() {

        }

        def cleanup() {

        }

        void "my test method"() {
            expect:
                1 == 1
        }
    }
    MySpec
    ''')

        then: "It implements TransactionManagerAware"
        mySpec.getDeclaredMethod('setup')
        mySpec.getDeclaredMethod('$tt__setup', TransactionStatus)
        mySpec.getDeclaredMethod('cleanup')
        mySpec.getDeclaredMethod('$tt__cleanup', TransactionStatus)

        mySpec.getDeclaredMethod('$spock_feature_0_0')
        mySpec.getDeclaredMethod('$tt__$spock_feature_0_0', TransactionStatus)
    }

    void "Test @Rollback when applied to Spock specifications and where blocks"() {
        when: "A new instance of a class with a @Transactional method is created that subclasses another transactional class"
        Class mySpec = new GroovyShell().evaluate('''
import grails.gorm.transactions.Rollback
import spock.lang.Specification

@Rollback
class DemoSpec extends Specification {

    def "test toUpperCase"() {
        given:
        def result = value.toUpperCase()

        expect:
        result == expectedResult

        where:
        value | expectedResult
        'King Crimson' | 'KING CRIMSON\'
        'Riverside'    | 'RIVERSIDE\'
    }
}
    DemoSpec
    ''')

        then: "It implements TransactionManagerAware"
        mySpec.getDeclaredMethod('$spock_feature_0_0', Object, Object)
        mySpec.getDeclaredMethod('$spock_feature_0_0proc', Object, Object)
        mySpec.getDeclaredMethod('$spock_feature_0_0prov0')

        !ReflectionUtils.findMethod(mySpec, '$tt__$spock_feature_0_0prov0', TransactionStatus)
        ReflectionUtils.findMethod(mySpec, '$tt__$spock_feature_0_0', Object, Object, TransactionStatus)
        !ReflectionUtils.findMethod(mySpec, '$tt__$spock_feature_0_0proc', Object, Object, TransactionStatus)
    }

    void "Test @Rollback when applied to Spock specifications on a method and where blocks"() {
        when: "A new instance of a class with a @Transactional method is created that subclasses another transactional class"
        Class mySpec = new GroovyShell().evaluate('''
import grails.gorm.transactions.Rollback
import spock.lang.Specification

class DemoSpec extends Specification {

    @Rollback
    def "test toUpperCase"() {
        given:
        def result = value.toUpperCase()

        expect:
        result == expectedResult

        where:
        value | expectedResult
        'King Crimson' | 'KING CRIMSON\'
        'Riverside'    | 'RIVERSIDE\'
    }
}
    DemoSpec
    ''')

        then: "It implements TransactionManagerAware"
        mySpec.getDeclaredMethod('$spock_feature_0_0', Object, Object)
        mySpec.getDeclaredMethod('$spock_feature_0_0proc', Object, Object)
        mySpec.getDeclaredMethod('$spock_feature_0_0prov0')

        !ReflectionUtils.findMethod(mySpec, '$tt__$spock_feature_0_0prov0', TransactionStatus)
        ReflectionUtils.findMethod(mySpec, '$tt__$spock_feature_0_0', Object, Object, TransactionStatus)
        !ReflectionUtils.findMethod(mySpec, '$tt__$spock_feature_0_0proc', Object, Object, TransactionStatus)
    }


    @Issue('#701')
    void "Test @Transactional with a datasource specified isn't TransactionManager aware, but has appropriate autowired and qualifier"() {
        when: "A new instance of a class with a @Transactional method is created that subclasses another transactional class"
        def bookService = new GroovyShell().evaluate('''
    import grails.gorm.transactions.*
    import org.springframework.transaction.PlatformTransactionManager
    import org.springframework.transaction.TransactionStatus
    import org.springframework.transaction.annotation.Isolation
    import org.springframework.transaction.annotation.Propagation


    class BookService {
        @Transactional(readOnly = true, timeout = 1000, isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW, connection = "foo")
        TransactionStatus readBook() {
             return transactionStatus
        }

        int add(int a, int b) {
            a + b
        }
    }

    new BookService()
    ''')

        then: "It implements TransactionManagerAware"
        bookService.getClass().getMethod("setTransactionManager", PlatformTransactionManager)
        bookService.getClass().getMethod("setTargetDatastore", MultipleConnectionSourceCapableDatastore[]).getAnnotation(Autowired)


    }

    @Issue('GRAILS-10402')
    void "Test @Transactional annotation with inheritance"() {
        given: "A new instance of a class with a @Transactional method is created that subclasses another transactional class"
        def bookService = new GroovyShell().evaluate(/**/'''
    import grails.gorm.transactions.*
    import org.springframework.transaction.PlatformTransactionManager
    import org.springframework.transaction.TransactionStatus
    import org.springframework.transaction.annotation.Isolation
    import org.springframework.transaction.annotation.Propagation

    @Transactional
    class ParentService {
           void doWork() {}
    }

    class BookService extends ParentService{

        void updateBook() {

        }

        @Transactional(readOnly = true, timeout = 1000, isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
        TransactionStatus readBook() {
             return transactionStatus
        }

        int add(int a, int b) {
            a + b
        }
    }

    new BookService()
    ''')


        when: "A transactionManager is set"
        final transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        then: "It is not null"
        bookService.transactionManager != null

        when: "A non-transactional method is called"
        bookService.updateBook()

        then: "The transaction was not started"
        transactionManager.transactionStarted == false

        when: "A transactional method is called"
        bookService.readBook()

        then: "The transaction was started"
        transactionManager.transactionStarted == true

        when: "A parent method that starts a transactiona is called"
        transactionManager.transactionStarted = false
        bookService.doWork()

        then: "The transaction was started"
        transactionManager.transactionStarted == true


    }

    void "Test that overriding the transaction manager with a custom setter works"() {
        given: "A new instance of a class with a @Transactional method is created"
        def bookService = new GroovyShell().evaluate('''
import grails.gorm.transactions.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.beans.factory.annotation.*

@Transactional
class BookService {

    private PlatformTransactionManager transactionManager

    @Autowired
    @Qualifier("transactionManager_configurationData")
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager
    }
    @Transactional(readOnly = true, timeout = 1000, isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    TransactionStatus readBook() {
         return transactionStatus
    }

    void updateBook() {

    }
    int add(int a, int b) {
        a + b
    }
}

new BookService()
''')


        when: "A transactionManager is set"
        final transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        then: "It is not null"
        bookService.transactionManager != null

        when: "A transactional method is called"
        bookService.updateBook()

        then: "The transaction was started"
        transactionManager.transactionStarted == true


        when: "A transactional method that takes arguments is called"
        def result = bookService.add(1, 2)

        then: "THe variables can be referenced"
        result == 3

        when: "When a read-only transaction is created"
        DefaultTransactionStatus status = (DefaultTransactionStatus) bookService.readBook()

        then: "The transaction definition is read-only"
        status.isReadOnly()
    }

    void "Test that a @Transactional annotation on a class results in a call to TransactionTemplate"() {
        given: "A new instance of a class with a @Transactional method is created"
        def bookService = new GroovyShell().evaluate('''
import grails.gorm.transactions.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation

@Transactional
class BookService {

    void updateBook() {

    }

    @Transactional(readOnly = true, timeout = 1000, isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    TransactionStatus readBook() {
         return transactionStatus
    }

    int add(int a, int b) {
        a + b
    }
}

new BookService()
''')

        when: "A transactionManager is set"
        final transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        then: "It is not null"
        bookService.transactionManager != null

        when: "A transactional method is called"
        bookService.updateBook()

        then: "The transaction was started"
        transactionManager.transactionStarted == true


        when: "A transactional method that takes arguments is called"
        def result = bookService.add(1, 2)

        then: "THe variables can be referenced"
        result == 3

        when: "When a read-only transaction is created"
        DefaultTransactionStatus status = (DefaultTransactionStatus) bookService.readBook()

        then: "The transaction definition is read-only"
        status.isReadOnly()

    }

    void "Test that a @Transactional annotation on a method results in a call to TransactionTemplate"() {
        given: "A new instance of a class with a @Transactional method is created"
        def bookService = new GroovyShell().evaluate('''
import grails.gorm.transactions.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation

class BookService {

    @Transactional
    void updateBook() {

    }

    @Transactional(readOnly = true, timeout = 1000, isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    TransactionStatus readBook() {
         return transactionStatus
    }

    @Transactional
    int add(int a, int b) {
        a + b
    }
    
    @ReadOnly
    boolean testReadOnly() {
        transactionStatus.isReadOnly()
    }
}

new BookService()
''')


        when: "A transactionManager is set"
        final transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        then: "It is not null"
        bookService.transactionManager != null

        when: "A transactional method is called"
        bookService.updateBook()

        then: "The transaction was started"
        transactionManager.transactionStarted == true


        when: "A transactional method that takes arguments is called"
        def result = bookService.add(1, 2)

        then: "THe variables can be referenced"
        result == 3

        when: "When a read-only transaction is created"
        DefaultTransactionStatus status = (DefaultTransactionStatus) bookService.readBook()

        then: "The transaction definition is read-only"
        status.isReadOnly()
        bookService.testReadOnly()
    }

    @Issue("GRAILS-10557")
    void "Test rollback with @Transactional annotation"() {
        given: "A new instance of a class with a @Transactional method is created"
        def bookService = new GroovyShell().evaluate('''
import grails.gorm.annotation.transactions.*
import grails.gorm.transactions.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation

class BookService {

    @Transactional
    void throwRuntimeException() {
        throw new TestTransactionRuntimeException()
    }

    @Transactional
    void throwException() {
        throw new TestTransactionException()
    }

}

new BookService()
''')

        when: "A transactionManager is set"
        def transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        and: "A transactional method throw RuntimeException"
        bookService.throwRuntimeException()

        then: "The transaction was rolled back"
        thrown(TestTransactionRuntimeException)
        transactionManager.transactionRolledBack == true

        when: "A transactionManager is set"
        transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        and: "A transactional method throw RuntimeException"
        bookService.throwException()

        then: "The transaction was rolled back"
        thrown(TestTransactionException)
        transactionManager.transactionRolledBack == true
    }

    @Issue("GRAILS-10564")
    void "Test rollback with @Transactional annotation attributes"() {
        given: "A new instance of a class with a @Transactional method is created"
        def bookService = new GroovyShell().evaluate('''
import grails.gorm.transactions.*
import grails.gorm.annotation.transactions.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation

class BookService {

    @Transactional(noRollbackFor = [TestTransactionRuntimeException])
    void noRollbackForMethod() {
        throw new TestTransactionRuntimeException()
    }

    @Transactional(noRollbackForClassName = ["TestTransactionRuntimeException"])
    void noRollbackForClassNameMethod() {
        throw new TestTransactionRuntimeException()
    }

    @Transactional(rollbackFor = TestTransactionException)
    void rollbackForMethod() {
        throw new TestTransactionException()
    }

    @Transactional(rollbackForClassName = "TestTransactionException")
    void rollbackForClassNameMethod() {
        throw new TestTransactionException()
    }

}

new BookService()
''')


        when: "A transactionManager is set"
        def transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        and: "A transactional method throw RuntimeException"
        bookService.noRollbackForMethod()

        then: "The transaction wasn't rolled back"
        thrown(TestTransactionRuntimeException)
        transactionManager.transactionRolledBack == false

        when: "A transactionManager is set"
        transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        and: "A transactional method throw RuntimeException"
        bookService.noRollbackForClassNameMethod()

        then: "The transaction wasn't rolled back"
        thrown(TestTransactionRuntimeException)
        transactionManager.transactionRolledBack == false

        when: "A transactionManager is set"
        transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        and: "A transactional method throw Exception"
        bookService.rollbackForMethod()

        then: "The transaction was rolled back"
        thrown(TestTransactionException)
        transactionManager.transactionRolledBack == true

        when: "A transactionManager is set"
        transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        and: "A transactional method throw Exception"
        bookService.rollbackForClassNameMethod()

        then: "The transaction was rolled back"
        thrown(TestTransactionException)
        transactionManager.transactionRolledBack == true
    }

    TestTransactionManager getPlatformTransactionManager() {
        def dataSource = new DriverManagerDataSource("jdbc:h2:mem:${TransactionalTransformSpec.name};LOCK_TIMEOUT=10000", "sa", "")

        // this may not be necessary...
        dataSource.driverClassName = "org.h2.Driver"

        return new TestTransactionManager(dataSource) {}
    }

    @Issue(['GRAILS-11145', 'GRAILS-11134'])
    void "Test inheritRollbackOnly attribute"() {
        given:
        def bookService = new GroovyShell().evaluate('''
    import grails.gorm.transactions.*
    import org.springframework.transaction.PlatformTransactionManager
    import org.springframework.transaction.TransactionStatus
    import org.springframework.transaction.annotation.Isolation
    import org.springframework.transaction.annotation.Propagation

    @Transactional
    class BookService {

        void updateBook() {
            doNestedUpdate()
        }

        void doNestedUpdate() {
            transactionStatus.setRollbackOnly()
        }
    }

    new BookService()
    ''')
        final transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager
        when: "A transactional method containing setRollbackOnly in nested transaction template is called"
        bookService.updateBook()
        then: "The test passes without UnexpectedRollbackException"
        1 == 1
    }

    @Issue(['GRAILS-11145', 'GRAILS-11134'])
    void "Test disabling inheritRollbackOnly"() {
        given:
        def bookService = new GroovyShell().evaluate('''
    import grails.gorm.transactions.*
    import org.springframework.transaction.PlatformTransactionManager
    import org.springframework.transaction.TransactionStatus
    import org.springframework.transaction.annotation.Isolation
    import org.springframework.transaction.annotation.Propagation

    @Transactional(inheritRollbackOnly=false)
    class BookService {

        void updateBook() {
            doNestedUpdate()
        }

        void doNestedUpdate() {
            transactionStatus.setRollbackOnly()
        }

        void doRollback() {
            transactionStatus.setRollbackOnly()
        }
    }

    new BookService()
    ''')
        final transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager
        when: "A transactional method containing setRollbackOnly in nested transaction template is called"
        bookService.updateBook()
        then: "UnexpectedRollbackException is thrown"
        thrown UnexpectedRollbackException
        when:
        bookService.doRollback()
        then: "no exception should be thrown when there are no nested transactions"
        1 == 1
    }

    void "Test rollback transformation"() {
        given:
        def bookService = new GroovyShell().evaluate('''
    import grails.gorm.transactions.*
    import org.springframework.transaction.TransactionStatus

    @Rollback
    class BookService {


        TransactionStatus doRollback() {
            def status = transactionStatus
            return  transactionStatus
        }
    }

    new BookService()
    ''')
        final transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager
        when: "A method is called"
        TransactionStatus status = bookService.doRollback()
        then: "Then the transaction has been rolled back"
        status.isRollbackOnly()
    }

    void "Test that a @Transactional annotation on a method sets name of transaction"() {
        given:"A new instance of a class with a @Transactional method is created"
        def bookService = new GroovyShell().evaluate('''
package foo
import grails.gorm.transactions.*
class BookService {
    @Transactional
    void updateBook() {
    }
}
new BookService()
''')

        when:"A transactionManager is set"
        final transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        then:"It is not null"
        bookService.transactionManager != null

        when:"When a transactional method is called"
        bookService.updateBook()

        then:"transaction name is foo.BookService.updateBook"
        transactionManager.definition.name == 'foo.BookService.updateBook'
    }

    void "Test that a @Transactional annotation on a class sets name of transaction"() {
        given:"A new instance of a class with a @Transactional method is created"
        def bookService = new GroovyShell().evaluate('''
package foo
import grails.gorm.transactions.*
@Transactional
class BookService {
    void updateBook() {
    }
}
new BookService()
''')

        when:"A transactionManager is set"
        final transactionManager = getPlatformTransactionManager()
        bookService.transactionManager = transactionManager

        then:"It is not null"
        bookService.transactionManager != null

        when:"When a method on a transactional class is called"
        bookService.updateBook()

        then:"transaction name is foo.BookService.updateBook"
        transactionManager.definition.name == 'foo.BookService.updateBook'
    }

    void 'test CompileStatic on a method in a class marked with Transactional'() {
        given:
        def gcl = new GroovyClassLoader()

        when:
        gcl.parseClass('''
package demo

@grails.gorm.transactions.Transactional
class SomeClass {
    @groovy.transform.CompileStatic
    def someMethod() {
        int x = 'Jeff'.lastName()
    }
}
''')
        then:
        MultipleCompilationErrorsException ex = thrown()
        ex.message.contains 'Cannot find matching method java.lang.String#lastName()'

    }

    void "test transactional behavior is applied to getter methods without a setter"() {
        when:
        def someClass = new GroovyShell().evaluate('''
package demo

    import grails.gorm.transactions.*
    import org.springframework.transaction.support.*
    
@Transactional
class SomeClass {

    public void setAge(String name) {
        assert !TransactionSynchronizationManager.isActualTransactionActive()
    }

    public String getAge() {
        assert !TransactionSynchronizationManager.isActualTransactionActive()
        'valid'
    }

    public String getPhone() {
        assert TransactionSynchronizationManager.isActualTransactionActive()
        'valid'
    }
}

new SomeClass()
''')

        final transactionManager = getPlatformTransactionManager()
        someClass.transactionManager = transactionManager
        someClass.setAge('x')

        then:
        someClass.getAge()
        someClass.getPhone()
    }

    void "test transactional behavior is applied to methods that aren't setters but start with set"() {
        when:
        def someClass = new GroovyShell().evaluate('''
package demo

    import grails.gorm.transactions.*
    import org.springframework.transaction.support.*
    
@Transactional
class SomeClass {

    public void setupSessionAfterLogin(String username) {
        assert TransactionSynchronizationManager.isActualTransactionActive()
    }

}
new SomeClass()
''')

        final transactionManager = getPlatformTransactionManager()
        someClass.transactionManager = transactionManager
        someClass.setupSessionAfterLogin('x')

        then:
        noExceptionThrown()
    }
}


@grails.gorm.transactions.Transactional
class TransactionalTransformSpecService implements InitializingBean {
    String name

    public TransactionStatus process() {
        return transactionStatus
    }

    @NotTransactional
    public boolean isActualTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive()
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        assert !TransactionSynchronizationManager.isActualTransactionActive()
    }

    public void setName(String name) {
        assert !TransactionSynchronizationManager.isActualTransactionActive()
        this.name = name
    }

    public String getName() {
        assert !TransactionSynchronizationManager.isActualTransactionActive()
        name
    }

    public boolean isActive() {
        TransactionSynchronizationManager.isActualTransactionActive()
    }
}


class TestTransactionManager extends DataSourceTransactionManager {
    boolean transactionStarted = false
    boolean transactionRolledBack = false
    TransactionDefinition definition = null

    TestTransactionManager(DataSource dataSource) {
        super(dataSource)
    }

    @Override
    protected Object doGetTransaction() {
        transactionStarted = true
        return super.doGetTransaction()
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        transactionRolledBack = true
        super.doRollback(status)
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        this.definition = definition
        super.doBegin(transaction, definition)
    }
}

class TestTransactionRuntimeException extends RuntimeException {
}

class TestTransactionException extends Exception {
}

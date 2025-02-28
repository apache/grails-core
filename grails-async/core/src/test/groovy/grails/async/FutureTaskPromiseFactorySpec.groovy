package grails.async

import grails.async.decorator.PromiseDecorator
import org.grails.async.factory.future.CachedThreadPoolPromiseFactory
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ExecutionException

/**
 * Created by graemerocher on 29/03/2017.
 */
class FutureTaskPromiseFactorySpec extends Specification {
    
    void setup() {
        Promises.promiseFactory = new CachedThreadPoolPromiseFactory()
    }

    void cleanup() {
        Promises.promiseFactory = null
    }

    void 'Test add promise decorator'() {
        
        when: 'a decorator is added'
            def decorator = { Closure c -> return { "*${c.call(*it)}*" } } as PromiseDecorator
            def p = Promises.createPromise({ 10 }, [decorator])
            def result = p.get()

        then: 'the result is decorated'
            result == '*10*'
    }

    void 'Test promise map handling'() {

        when: 'a promise map is created'
            def map = Promises.createPromise(one: { 1 }, two: { 1 + 1 }, four: { 2 * 2 })
            def result= map.get()

        then: 'the map is valid'
            result == [one: 1, two: 2, four: 4]
    }

    void 'Test promise list handling'() {

        when: 'a promise list is created from two promises'
            def p1 = Promises.createPromise({ 1 + 1 })
            def p2 = Promises.createPromise({ 2 + 2 })
            def list = Promises.createPromise(p1, p2)
            List<Integer> result = null
            list.onComplete { List l -> result = l }

        then: 'the result is correct'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result
                assert result == [2, 4]
            }

        when: 'a promise list is created from two closures'
            list = Promises.createPromise({ 2 + 2 }, { 4 + 4 })
            list.onComplete { result = it }

        then: 'the result is correct'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result == [4, 8]
            }
    }

    void 'Test promise onComplete handling'() {

        when: 'a promise is executed with an onComplete handler'
            def promise = Promises.createPromise { 1 + 1 }
            def result = null
            def hasError = false
            promise.onComplete { result = it }.get()
            promise.onError { hasError = true }.get()

        then: 'the onComplete handler is invoked and the onError handler is ignored'
            result == 2
            hasError == false
    }

    void 'Test promise onError handling'() {
        
        when: 'a promise is executed with an onComplete handler'
            def promise = Promises.createPromise { throw new RuntimeException('bad') }
            def result = null
            Throwable error = null
            promise.onComplete { result = it }
            promise.onError { error = it }.get()

        then: 'the onComplete handler is invoked and the onError handler is ignored'
            thrown(ExecutionException)
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert !result
                assert error
            }
    }

    void 'Test promise chaining'() {
        
        when: 'a promise is chained'
            def promise = Promises.createPromise { 1 + 1 }
            promise = promise.then { it * 2 } then { it + 6 }
            def val = promise.get()

        then: 'the chain is executed'
           val == 10
    }

    void 'Test promise chaining with exception'() {

        when: 'a promise is chained'
            def promise = Promises.createPromise { 1 + 1 }
            promise = promise.then { it * 2 } then { throw new RuntimeException('bad') } then { it + 6 }
            def val = promise.get()

        then: 'the chain is executed'
            thrown RuntimeException
            val == null
    }

    @Issue('GRAILS-10152')
    void 'Test promise closure is not executed multiple times if it returns null'() {

        given: 'a closure that returns null'
            Closure callable =  Mock(Closure) { call() >> null }

        when: 'a promise is created'
            Promises.waitAll([Promises.createPromise(callable), Promises.createPromise(callable)])

        then: 'the closure is executed twice'
            2 * callable.call()
    }
}

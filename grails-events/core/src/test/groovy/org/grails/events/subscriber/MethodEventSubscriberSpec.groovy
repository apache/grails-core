package org.grails.events.subscriber

import grails.events.subscriber.MethodSubscriber
import spock.lang.Specification

/**
 * Created by graemerocher on 28/03/2017.
 */
class MethodEventSubscriberSpec extends Specification {

    void 'Test convert method argument'() {

        given: 'a class with a method foo(Integer)'
            def testService = new TestService()

        when: 'we create a method subscriber on that method'
            def subscriber = new MethodSubscriber(testService, TestService.getMethod('foo', Integer))

        then: 'the results of invoking the subscriber to be correct'
            subscriber.call(1) == 2
            subscriber.call('1') == 2
            subscriber.call('') == null
    }
}

class TestService {
    def foo(Integer num) {
       return num + 1
    }
}

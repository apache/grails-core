package org.grails.events.spring

import grails.events.EventPublisher
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.stereotype.Component
import spock.lang.Specification

/**
 * Created by graemerocher on 27/03/2017.
 */
class EventPublisherSpec extends Specification {

    def 'Test event publisher within Spring'() {

        given: 'a Spring application context with an event bus'
            AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext()
            def bus = new SpringEventBus(applicationContext)
            applicationContext.beanFactory.registerSingleton("eventBus", bus)

        and: 'we register a publisher in the application context'
            applicationContext.register(MyPublisher)
            applicationContext.refresh()
            MyPublisher publisher = applicationContext.getBean(MyPublisher)

        and: 'we subscribe to an event'
            def result = null
            bus.on('test') { result = "good $it" }

        when: 'we publish an event'
            publisher.publish('test', 'data')

        then: 'the result is correct'
            result == "good data"
    }
}

@Component
class MyPublisher implements EventPublisher {
}

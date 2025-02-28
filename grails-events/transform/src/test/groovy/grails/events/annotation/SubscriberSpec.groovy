package grails.events.annotation

import grails.events.bus.EventBus
import grails.events.subscriber.MethodSubscriber
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.grails.events.gorm.GormAnnotatedSubscriber
import org.grails.events.transform.AnnotatedSubscriber
import spock.lang.Specification

/**
 * Created by graemerocher on 29/03/2017.
 */
class SubscriberSpec extends Specification {

    void 'test subscriber transform'() {

        given: 'a class with a method annotated with the @Subscriber annotation'
            def service = new GroovyClassLoader().parseClass('''
            class TestService {
                int total = 0
                @grails.events.annotation.Subscriber('total')
                void onSum(int num) { total += num }
            }''').getDeclaredConstructor().newInstance()
            def methodObject = service.getClass().getDeclaredMethod('onSum', int)

        when: 'we set the target event bus'
            def eventBus = Mock(EventBus)
            service.targetEventBus = eventBus

        then: 'the class is an instance of AnnotatedSubscriber'
            ClassPropertyFetcher.forClass(service.class).getPropertyValue('lazyInit') == false
            service instanceof AnnotatedSubscriber

        when: 'we register the methods'
            service.registerMethods()

        then: 'the event bus is subscribed to the event'
            1 * eventBus.subscribe('total', new MethodSubscriber(service, methodObject))
    }

    void 'Test gorm event subscriber transform'() {

        given: 'a class with a method annotated with the @Subscriber that takes a PreInsertEvent arg'
            def service = new GroovyShell().evaluate('''
                import org.grails.datastore.mapping.engine.event.PreInsertEvent
                import grails.events.annotation.*
                
                class TestService {
    
                    @Subscriber
                    void onInsert(PreInsertEvent event) { /* whatever */ }
                }
                return TestService
                ''').getDeclaredConstructor().newInstance()
                def methodObject = service.getClass().getDeclaredMethod('onInsert', PreInsertEvent)

        when: 'we set the target event bus'
            def eventBus = Mock(EventBus)
            service.targetEventBus = eventBus

        then: 'the class is an instance of GormAnnotatedSubscriber'
            ClassPropertyFetcher.forClass(service.getClass()).getPropertyValue('lazyInit') == false
            service instanceof GormAnnotatedSubscriber

        when: 'we register the methods'
            service.registerMethods()

        then: 'the event bus is subscribed to the event'
            1 * eventBus.subscribe("gorm:preInsert", new MethodSubscriber(service, methodObject))
    }
}

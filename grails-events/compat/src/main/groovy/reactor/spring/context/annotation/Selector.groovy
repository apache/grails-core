package reactor.spring.context.annotation

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * @deprecated Use {@link grails.events.subscriber.Subscriber} instead
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@GroovyASTTransformationClass("org.grails.events.transform.SubscriberTransform")
@interface Selector {
    /**
     * @return The id of the event
     */
    String value()
}
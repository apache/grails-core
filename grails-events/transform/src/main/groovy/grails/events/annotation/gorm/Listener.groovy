package grails.events.annotation.gorm

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * A Listener is like a {@link grails.events.annotation.Subscriber} for GORM events, but is dispatched synchronously
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@GroovyASTTransformationClass("org.grails.events.transform.SubscriberTransform")
@interface Listener {
    /**
     * @return The types this listener listens for
     */
    Class[] value() default []
}
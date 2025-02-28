package grails.events.annotation

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Transforms a method into a subscriber for an event
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@GroovyASTTransformationClass("org.grails.events.transform.SubscriberTransform")
@interface Subscriber {
    /**
     * @return The id of the event
     */
    String value() default ""
}
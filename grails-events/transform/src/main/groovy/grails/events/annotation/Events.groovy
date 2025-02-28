package grails.events.annotation

import org.springframework.transaction.event.TransactionPhase

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Allows the definition of common event settings at the class level
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@interface Events {
    /**
     * @return The transaction phase to subscribe on
     */
    TransactionPhase phase() default TransactionPhase.AFTER_COMMIT

    /**
     * @return The namespace of the events
     */
    String namespace() default ""

    /**
     * @return The id of the event to notify in the case of an error
     */
    String error() default ""
}
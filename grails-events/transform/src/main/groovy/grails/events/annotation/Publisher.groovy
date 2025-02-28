package grails.events.annotation

import org.codehaus.groovy.transform.GroovyASTTransformationClass
import org.grails.datastore.gorm.transform.GormASTTransformationClass
import org.springframework.transaction.event.TransactionPhase

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target


/**
 * Transforms a method so the return value is emitted as an event
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@GroovyASTTransformationClass("org.grails.datastore.gorm.transform.OrderedGormTransformation")
@GormASTTransformationClass("org.grails.events.transform.PublisherTransform")
@interface Publisher {
    /**
     * @return The id of the event
     */
    String value() default ""

    /**
     * @return The transaction phase to subscribe on
     */
    TransactionPhase phase() default TransactionPhase.AFTER_COMMIT

    /**
     * @return The id of the event to notify in the case of an error
     */
    String error() default ""

}
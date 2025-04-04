package grails.gorm.transactions

import org.codehaus.groovy.transform.GroovyASTTransformationClass
import org.grails.datastore.gorm.transactions.transform.RollbackTransform
import org.grails.datastore.gorm.transform.GormASTTransformationClass

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * A transforms that applies a transaction that always rolls back. Useful for testing. See {@link Transactional}
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@Target([ElementType.METHOD, ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@GroovyASTTransformationClass("org.grails.datastore.gorm.transform.OrderedGormTransformation")
@GormASTTransformationClass("org.grails.datastore.gorm.transactions.transform.RollbackTransform")
@interface Rollback {
    /**
     * The connection to rollback for
     */
    String value() default ""
}
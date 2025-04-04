package grails.gorm.rx.services;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;
import org.grails.datastore.gorm.transform.GormASTTransformationClass;
import rx.schedulers.Schedulers;

import java.lang.annotation.*;

/**
 * A transformation that transforms the body of a method to return an Observable that runs on the IO Scheduler
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
@Inherited
@Documented
@GroovyASTTransformationClass("org.grails.datastore.gorm.transform.OrderedGormTransformation")
@GormASTTransformationClass("org.grails.gorm.rx.transform.RxScheduleIOTransformation")
public @interface RxSchedule {

    /**
     * @return A closure that returns the scheduler to run on. Default is {@link Schedulers#io()}
     */
    Class scheduler() default Object.class;
    /**
     * @return Whether the underlying query method returns a single result of an iterable
     */
    boolean singleResult() default false;
}

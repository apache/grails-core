package grails.gorm.services;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;
import org.grails.datastore.gorm.services.ServiceImplementer;
import org.grails.datastore.gorm.services.ServiceImplementerAdapter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes any class into a GORM {@link org.grails.datastore.mapping.services.Service}
 *
 * @since 6.1
 * @author Graeme Rocher
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@GroovyASTTransformationClass("org.grails.datastore.gorm.services.transform.ServiceTransformation")
public @interface Service {

    /**
     * @return The domain class this service operates with
     */
    Class value() default Object.class;

    /**
     * @return The name of the service, by default this will the class name decapitalized. ie. BookService = bookService
     */
    String name() default "";

    /**
     * @return Any additional implementers to apply
     */
    Class<? extends ServiceImplementer>[] implementers() default {};

    /**
     * @return Any additional adapters to apply
     */
    Class<? extends ServiceImplementerAdapter>[] adapters() default {};
}

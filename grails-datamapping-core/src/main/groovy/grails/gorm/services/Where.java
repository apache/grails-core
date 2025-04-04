package grails.gorm.services;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allow specifying the where query to execute
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Where {
    Class value();
}

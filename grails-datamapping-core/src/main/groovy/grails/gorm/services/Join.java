package grails.gorm.services;

import jakarta.persistence.criteria.JoinType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allow specifying the join to services
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Join {
    /**
     * @return The property name to join on
     */
    String value();

    /**
     * @return The join type
     */
    JoinType[] type() default {};
}

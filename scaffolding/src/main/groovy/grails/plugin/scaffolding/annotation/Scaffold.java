package grails.plugin.scaffolding.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Scaffold {
    Class<?> value() default Void.class;
    Class<?> domain() default Void.class;
    boolean readOnly() default false;
}

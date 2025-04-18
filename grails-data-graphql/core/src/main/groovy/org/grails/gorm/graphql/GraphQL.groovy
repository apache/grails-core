package org.grails.gorm.graphql

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation used to supply metadata to GraphQL. Can be used
 * on entites related to graphl mapped domains even if the
 * domain itself isn't mapped. Also useful to annotate on
 * enumerations because there is no other alternative.
 *
 * The default deprecation reason is "Deprecated"
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@Target([ElementType.TYPE, ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface GraphQL {

    String value() default ''

    boolean deprecated() default false

    String deprecationReason() default ''
}

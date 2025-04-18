package org.grails.gorm.graphql.types.scalars.coercing.jsr310

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Default {@link LocalTime} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@InheritConstructors
@CompileStatic
class LocalTimeCoercion extends Jsr310Coercion<LocalTime> {

    @Override
    LocalTime parse(String value, String format) {
        LocalTime.parse((CharSequence) value, DateTimeFormatter.ofPattern(format))
    }

    @Override
    Class getTypeClass() {
        LocalTime
    }
}

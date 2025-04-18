package org.grails.gorm.graphql.types.scalars.coercing.jsr310

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Default {@link LocalDateTime} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@InheritConstructors
@CompileStatic
class LocalDateTimeCoercion extends Jsr310Coercion<LocalDateTime> {

    @Override
    LocalDateTime parse(String value, String format) {
        LocalDateTime.parse((CharSequence) value, DateTimeFormatter.ofPattern(format))
    }

    @Override
    Class getTypeClass() {
        LocalDateTime
    }
}

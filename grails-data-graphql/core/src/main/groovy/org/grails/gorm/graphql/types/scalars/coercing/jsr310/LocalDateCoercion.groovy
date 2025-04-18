package org.grails.gorm.graphql.types.scalars.coercing.jsr310

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Default {@link LocalDate} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@InheritConstructors
@CompileStatic
class LocalDateCoercion extends Jsr310Coercion<LocalDate> {

    @Override
    LocalDate parse(String value, String format) {
        LocalDate.parse((CharSequence) value, DateTimeFormatter.ofPattern(format))
    }

    @Override
    Class getTypeClass() {
        LocalDate
    }
}

package org.grails.gorm.graphql.types.scalars.coercing.jsr310

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Default {@link ZonedDateTime} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@InheritConstructors
@CompileStatic
class ZonedDateTimeCoercion extends Jsr310Coercion<ZonedDateTime> {

    @Override
    ZonedDateTime parse(String value, String format) {
        ZonedDateTime.parse((CharSequence)value, DateTimeFormatter.ofPattern(format))
    }

    @Override
    Class getTypeClass() {
        ZonedDateTime
    }
}

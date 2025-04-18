package org.grails.gorm.graphql.types.scalars.coercing.jsr310

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Default {@link OffsetDateTime} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@InheritConstructors
@CompileStatic
class OffsetDateTimeCoercion extends Jsr310Coercion<OffsetDateTime> {

    @Override
    OffsetDateTime parse(String value, String format) {
        OffsetDateTime.parse((CharSequence)value, DateTimeFormatter.ofPattern(format))
    }

    @Override
    Class getTypeClass() {
        OffsetDateTime
    }
}

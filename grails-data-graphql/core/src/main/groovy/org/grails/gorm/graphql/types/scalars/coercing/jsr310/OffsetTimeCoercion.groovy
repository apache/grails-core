package org.grails.gorm.graphql.types.scalars.coercing.jsr310

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import java.time.OffsetTime
import java.time.format.DateTimeFormatter

/**
 * Default {@link OffsetTime} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@InheritConstructors
@CompileStatic
class OffsetTimeCoercion extends Jsr310Coercion<OffsetTime> {

    @Override
    OffsetTime parse(String value, String format) {
        OffsetTime.parse((CharSequence)value, DateTimeFormatter.ofPattern(format))
    }

    @Override
    Class getTypeClass() {
        OffsetTime
    }
}

package grails.gorm.time

import groovy.transform.CompileStatic

import java.time.Instant
import java.time.OffsetDateTime

/**
 * A trait to convert a {@link java.time.OffsetDateTime} to and from a long
 *
 * @author James Kleeh
 */
@CompileStatic
trait OffsetDateTimeConverter implements TemporalConverter<OffsetDateTime> {

    @Override
    Long convert(OffsetDateTime value) {
        value.toInstant().toEpochMilli()
    }

    @Override
    OffsetDateTime convert(Long value) {
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(value), systemOffset)
    }

}

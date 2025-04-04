package grails.gorm.time

import groovy.transform.CompileStatic

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A trait to convert a {@link java.time.LocalDateTime} to and from a long
 *
 * @author James Kleeh
 */
@CompileStatic
trait LocalDateTimeConverter implements TemporalConverter<LocalDateTime> {

    @Override
    Long convert(LocalDateTime value) {
        value.toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    @Override
    LocalDateTime convert(Long value) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.of('UTC'))
    }
}

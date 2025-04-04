package grails.gorm.time

import groovy.transform.CompileStatic

import java.time.LocalTime

/**
 * A trait to convert a {@link LocalTime} to and from a long
 *
 * @author James Kleeh
 */
@CompileStatic
trait LocalTimeConverter implements TemporalConverter<LocalTime> {

    @Override
    Long convert(LocalTime value) {
        value.toNanoOfDay()
    }

    @Override
    LocalTime convert(Long value) {
        LocalTime.ofNanoOfDay(value)
    }
}
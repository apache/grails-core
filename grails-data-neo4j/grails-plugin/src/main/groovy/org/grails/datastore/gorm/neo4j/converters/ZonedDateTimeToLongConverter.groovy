package org.grails.datastore.gorm.neo4j.converters

import groovy.transform.CompileStatic
import grails.gorm.time.ZonedDateTimeConverter
import org.springframework.core.convert.converter.Converter

import java.time.ZonedDateTime

/**
 * A class to implement {@link Converter} for {@link java.time.ZonedDateTime} to Long
 *
 * @author James Kleeh
 */
@CompileStatic
class ZonedDateTimeToLongConverter implements Converter<ZonedDateTime, Long>, ZonedDateTimeConverter {
}

package org.grails.datastore.gorm.neo4j.converters

import grails.gorm.time.OffsetDateTimeConverter
import groovy.transform.CompileStatic
import org.springframework.core.convert.converter.Converter

import java.time.OffsetDateTime

/**
 * A class to implement {@link Converter} for {@link java.time.OffsetDateTime} to Long
 *
 * @author James Kleeh
 */
@CompileStatic
class OffsetDateTimeToLongConverter implements Converter<OffsetDateTime, Long>, OffsetDateTimeConverter {
}

package org.grails.datastore.gorm.neo4j.converters

import groovy.transform.CompileStatic
import grails.gorm.time.OffsetDateTimeConverter
import org.springframework.core.convert.converter.Converter

import java.time.OffsetDateTime

/**
 * A class to implement {@link Converter} for Long to {@link java.time.OffsetDateTime}
 *
 * @author James Kleeh
 */
@CompileStatic
class LongToOffsetDateTimeConverter implements Converter<Long, OffsetDateTime>, OffsetDateTimeConverter {
}

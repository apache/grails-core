package org.grails.datastore.gorm.neo4j.converters

import groovy.transform.CompileStatic
import grails.gorm.time.LocalDateTimeConverter
import org.springframework.core.convert.converter.Converter

import java.time.LocalDateTime

/**
 * A class to implement {@link Converter} for Long to {@link java.time.LocalDateTime}
 *
 * @author James Kleeh
 */
@CompileStatic
class LongToLocalDateTimeConverter implements Converter<Long, LocalDateTime>, LocalDateTimeConverter {
}


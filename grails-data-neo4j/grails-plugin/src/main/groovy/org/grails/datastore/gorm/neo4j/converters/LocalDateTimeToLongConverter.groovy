package org.grails.datastore.gorm.neo4j.converters

import groovy.transform.CompileStatic
import grails.gorm.time.LocalDateTimeConverter
import org.springframework.core.convert.converter.Converter

import java.time.LocalDateTime

/**
 * A class to implement {@link Converter} for {@link LocalDateTime} to Long
 *
 * @author James Kleeh
 */
@CompileStatic
class LocalDateTimeToLongConverter implements Converter<LocalDateTime, Long>, LocalDateTimeConverter {
}

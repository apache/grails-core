package org.grails.datastore.gorm.neo4j.converters

import groovy.transform.CompileStatic
import grails.gorm.time.OffsetTimeConverter
import org.springframework.core.convert.converter.Converter

import java.time.OffsetTime

/**
 * A class to implement {@link Converter} for Long to {@link java.time.OffsetTime}
 *
 * @author James Kleeh
 */
@CompileStatic
class LongToOffsetTimeConverter implements Converter<Long, OffsetTime>, OffsetTimeConverter {
}

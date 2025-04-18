package org.grails.datastore.gorm.neo4j.converters

import groovy.transform.CompileStatic
import grails.gorm.time.OffsetTimeConverter
import org.springframework.core.convert.converter.Converter

import java.time.OffsetTime

/**
 * A class to implement {@link Converter} for {@link java.time.OffsetTime} to Long
 *
 * @author James Kleeh
 */
@CompileStatic
class OffsetTimeToLongConverter implements Converter<OffsetTime, Long>, OffsetTimeConverter {
}

package org.grails.datastore.gorm.neo4j.converters

import groovy.transform.CompileStatic
import grails.gorm.time.LocalTimeConverter
import org.springframework.core.convert.converter.Converter

import java.time.LocalTime

/**
 * A class to implement {@link Converter} for Long to {@link java.time.LocalTime}
 *
 * @author James Kleeh
 */
@CompileStatic
class LongToLocalTimeConverter implements Converter<Long, LocalTime>, LocalTimeConverter{
}

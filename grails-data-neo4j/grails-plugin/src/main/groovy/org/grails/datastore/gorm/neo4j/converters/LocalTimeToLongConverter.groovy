package org.grails.datastore.gorm.neo4j.converters

import groovy.transform.CompileStatic
import grails.gorm.time.LocalTimeConverter
import org.springframework.core.convert.converter.Converter

import java.time.LocalTime

/**
 * A class to implement {@link Converter} for {@link java.time.LocalTime} to Long
 *
 * @author James Kleeh
 */
@CompileStatic
class LocalTimeToLongConverter implements Converter<LocalTime, Long>, LocalTimeConverter {
}

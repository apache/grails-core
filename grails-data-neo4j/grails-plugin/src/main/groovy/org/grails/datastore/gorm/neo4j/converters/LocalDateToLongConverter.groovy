package org.grails.datastore.gorm.neo4j.converters

import grails.gorm.time.LocalDateConverter
import groovy.transform.CompileStatic
import org.springframework.core.convert.converter.Converter

import java.time.LocalDate

/**
 * A class to implement {@link Converter} for {@link java.time.LocalDate} to Long
 *
 * @author James Kleeh
 */
@CompileStatic
class LocalDateToLongConverter implements Converter<LocalDate, Long>, LocalDateConverter {
}

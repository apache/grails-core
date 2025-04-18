package org.grails.datastore.gorm.neo4j.converters

import grails.gorm.time.PeriodConverter
import groovy.transform.CompileStatic
import org.springframework.core.convert.converter.Converter

import java.time.Period

/**
 * A class to implement {@link Converter} for {@link java.time.Period} to String
 *
 * @author James Kleeh
 */
@CompileStatic
class PeriodToStringConverter implements Converter<Period, String>, PeriodConverter {
}

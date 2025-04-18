package org.grails.datastore.gorm.neo4j.converters

import grails.gorm.time.InstantConverter
import groovy.transform.CompileStatic
import org.springframework.core.convert.converter.Converter

import java.time.Instant

/**
 * A class to implement {@link Converter} for {@link java.time.Instant} to Long
 *
 * @author James Kleeh
 */
@CompileStatic
class InstantToLongConverter implements Converter<Instant, Long>, InstantConverter {
}

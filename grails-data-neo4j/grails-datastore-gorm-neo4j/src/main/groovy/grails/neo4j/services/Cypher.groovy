package grails.neo4j.services

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * An annotation for use on {@link grails.gorm.services.Service} interfaces to automatically implement Cypher queries
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
@interface Cypher {
    String value()
}
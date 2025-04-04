package org.grails.datastore.gorm.timestamp

import java.sql.Timestamp
import org.springframework.util.ClassUtils
import spock.lang.Specification

class DefaultTimestampProviderSpec extends Specification {
    DefaultTimestampProvider timestampProvider = new DefaultTimestampProvider()
    
    def supportedClasses = [Date, Timestamp, Long.TYPE, Long]
    
    def "timestamp provider should support java.util.Date, java.sql.Timestamp, long and Long"() {
        expect:
        supportedClasses.each {clazz ->
            assert timestampProvider.supportsCreating(clazz) == true
        }
    }
    
    def "timestamp provider should create java.util.Date, java.sql.Timestamp, long and Long"() {
        expect:
        supportedClasses.each {clazz ->
            println "type $clazz"
            def timestamp = timestampProvider.createTimestamp(clazz)
            println "timestamp $timestamp"
            assert timestamp.getClass() == ClassUtils.resolvePrimitiveIfNecessary(clazz)
        }
    }
    
    def "timestamp provider should create java.util.Date if given type is Object"() {
        when:
        def timestamp = timestampProvider.createTimestamp(Object)
        then:
        timestamp.getClass() == Date
    }

    //To support JSR310 date classes
    //LocalDateTime, LocalDate, LocalTime, OffsetDateTime, OffsetTime, ZonedDateTime all have a static now() method
    def "timestamp provider should instantiate class with static now method"() {
        when:
        def timestamp = timestampProvider.createTimestamp(Foo)
        then:
        timestamp.getClass() == Foo
    }

    static class Foo {
        static Foo now() {
            return new Foo()
        }
    }

}

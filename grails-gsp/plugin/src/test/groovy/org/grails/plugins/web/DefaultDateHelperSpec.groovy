package org.grails.plugins.web

import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.*
import java.time.format.DateTimeFormatter

class DefaultDateHelperSpec extends Specification {

    @Shared
    LocalDate localDate = LocalDate.of(1941, 1, 5)

    @Shared
    LocalTime localTime = LocalTime.of(8,0,0)

    @Shared
    DefaultGrailsTagDateHelper helper = new DefaultGrailsTagDateHelper()

    void "test getTimeZone"() {
        expect:
        helper.getTimeZone(TimeZone.getTimeZone('UTC')) == ZoneId.of("UTC")
        helper.getTimeZone(ZoneId.of("UTC")) == ZoneId.of("UTC")
        helper.getTimeZone("UTC") == ZoneId.of("UTC")
        helper.getTimeZone(null) == ZoneId.systemDefault()
    }

    void "test getFormatFromPattern"() {
        given:
        DateTimeFormatter format = helper.getFormatFromPattern("yyyy-MM-dd", ZoneId.of('UTC'), Locale.ENGLISH)

        expect:
        format.zone == ZoneId.of('UTC')
        format.format(localDate) == "1941-01-05"
    }

    void "test getDateFormat"() {
        given:
        DateTimeFormatter format

        when:
        format = helper.getDateFormat(style, ZoneId.of('UTC'), Locale.ENGLISH)

        then:
        format.zone == ZoneId.of('UTC')
        format.format(localDate) == expected

        where:
        style    | expected
        'FULL'   | 'Sunday, January 5, 1941'
        'LONG'   | 'January 5, 1941'
        'MEDIUM' | 'Jan 5, 1941'
        null     | '1/5/41'
    }

    @Unroll
    void "getTimeFormat for style #style returns #expected"(String style, String expected) {
        given:
        DateTimeFormatter format

        when:
        format = helper.getTimeFormat(style, ZoneId.of('UTC'), Locale.ENGLISH)

        then:
        format.zone == ZoneId.of('UTC')
        format.format(localTime) == expected

        where:
        style    | expected
        'LONG'   | '8:00:00 AM UTC'
        'MEDIUM' | '8:00:00 AM'
        null     | '8:00 AM'
    }

    @Requires({ jvm.isJava8() })
    @Unroll
    void "Java 8 - Full getTimeFormat for style #style returns #expected"(String style, String expected) {
        given:
        DateTimeFormatter format

        when:
        format = helper.getTimeFormat(style, ZoneId.of('UTC'), Locale.ENGLISH)

        then:
        format.zone == ZoneId.of('UTC')
        format.format(localTime) == expected

        where:
        style  | expected
        'FULL' | '8:00:00 AM UTC'
    }

    @IgnoreIf({ jvm.isJava8() })
    @Unroll
    void "Full getTimeFormat for style #style returns #expected"(String style, String expected) {
        given:
        DateTimeFormatter format

        when:
        format = helper.getTimeFormat(style, ZoneId.of('UTC'), Locale.ENGLISH)

        then:
        format.zone == ZoneId.of('UTC')
        format.format(localTime) == expected

        where:
        style  | expected
        'FULL' | '8:00:00 AM Coordinated Universal Time'
    }

    @Requires({ jvm.isJava8() })
    @Unroll("for getDateTimeFormat(#dateStyle, #timeStyle) => #expected")
    void "Java 8 - test getDateTimeFormat"(String dateStyle, String timeStyle, String expected) {
        given:
        DateTimeFormatter format

        when:
        format = helper.getDateTimeFormat(dateStyle, timeStyle, ZoneId.of('UTC'), Locale.ENGLISH)

        then:
        format.zone == ZoneId.of('UTC')
        format.format(LocalDateTime.of(localDate, localTime)) == expected

        where:
        dateStyle | timeStyle | expected
        'FULL'    | 'FULL'    | 'Sunday, January 5, 1941 8:00:00 AM UTC'
        'LONG'    | 'LONG'    | 'January 5, 1941 8:00:00 AM UTC'
        'MEDIUM'  | 'MEDIUM'  | 'Jan 5, 1941 8:00:00 AM'
        null      | null      | '1/5/41 8:00 AM'
    }

    @IgnoreIf({ jvm.isJava8() })
    @Unroll("for getDateTimeFormat(#dateStyle, #timeStyle) => #expected")
    void "test getDateTimeFormat"(String dateStyle, String timeStyle, String expected) {
        given:
        DateTimeFormatter format

        when:
        format = helper.getDateTimeFormat(dateStyle, timeStyle, ZoneId.of('UTC'), Locale.ENGLISH)

        then:
        format.zone == ZoneId.of('UTC')
        format.format(LocalDateTime.of(localDate, localTime)) == expected

        where:
        dateStyle | timeStyle | expected
        'FULL'    | 'FULL'    | 'Sunday, January 5, 1941 at 8:00:00 AM Coordinated Universal Time'
        'LONG'    | 'LONG'    | 'January 5, 1941 at 8:00:00 AM UTC'
        'MEDIUM'  | 'MEDIUM'  | 'Jan 5, 1941, 8:00:00 AM'
        null      | null      | '1/5/41, 8:00 AM'
    }

    void "test supportsDatePickers"() {
        expect:
        helper.supportsDatePicker(Date)
        helper.supportsDatePicker(LocalDate)
        helper.supportsDatePicker(LocalTime)
        helper.supportsDatePicker(LocalDateTime)
        helper.supportsDatePicker(OffsetDateTime)
        helper.supportsDatePicker(OffsetTime)
        helper.supportsDatePicker(ZonedDateTime)
    }

    void "test buildCalendar"() {
        //TemporalAccessors without date aren't designed to be supported here
        expect:
        helper.buildCalendar(new Date()) instanceof GregorianCalendar
        helper.buildCalendar(LocalDateTime.now()) instanceof GregorianCalendar
        helper.buildCalendar(LocalDate.now()) instanceof GregorianCalendar
        helper.buildCalendar(OffsetDateTime.now()) instanceof GregorianCalendar
        helper.buildCalendar(ZonedDateTime.now()) instanceof GregorianCalendar
    }

    void "test format"() {
        given:
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of('UTC'))
        DateTimeFormatter timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of('UTC'))
        DateTimeFormatter dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of('UTC'))

        LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime)

        OffsetDateTime offsetDateTime = localDateTime.atOffset(ZoneOffset.UTC)

        OffsetTime offsetTime = OffsetTime.of(localTime, ZoneOffset.UTC)

        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.of('UTC'))

        Date javaUtilDate = Date.from(zonedDateTime.toInstant())

        Calendar calendar = Calendar.instance
        calendar.setTime(javaUtilDate)

        Long seconds = javaUtilDate.time

        expect:
        helper.format(formatter, zonedDateTime) == '1941-01-05 08:00:00'
        helper.format(timeOnlyFormatter, offsetTime) == '08:00:00'
        helper.format(formatter, offsetDateTime) == '1941-01-05 08:00:00'
        helper.format(timeOnlyFormatter, localTime) == '08:00:00'
        helper.format(dateOnlyFormatter, localDate) == '1941-01-05'
        helper.format(formatter, localDateTime) == '1941-01-05 08:00:00'
        helper.format(formatter, javaUtilDate) == '1941-01-05 08:00:00'
        helper.format(formatter, calendar) == '1941-01-05 08:00:00'
        helper.format(formatter, seconds) == '1941-01-05 08:00:00'

        when:
        helper.format(formatter, null)

        then:
        thrown(IllegalArgumentException)
    }

    void "test format for java.sql.Date"() {

        given:
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of('UTC'))
        def date = new java.sql.Date(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())

        expect:
        helper.format(formatter, date) == '1941-01-05'
    }
}

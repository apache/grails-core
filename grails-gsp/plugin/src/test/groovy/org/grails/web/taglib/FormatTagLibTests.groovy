package org.grails.web.taglib

import org.grails.buffer.StreamCharBuffer
import org.grails.taglib.GrailsTagException
import org.junit.jupiter.api.Test
import spock.lang.Issue

import java.text.DecimalFormatSymbols

import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests for the FormatTagLib.
 *
 * @author Graeme Rocher
 * @since 0.6
 */
class FormatTagLibTests extends AbstractGrailsTagTests {

    @Test
    void testFormatBoolean() {
        messageSource.addMessage("default.boolean.true",request.locale, "Yeah!")
        messageSource.addMessage("default.boolean.false",request.locale, "Noooo!")

        def template = '<g:formatBoolean boolean="${theBoolean}" />'

        assertOutputEquals "Yeah!", template, [theBoolean:true]
        assertOutputEquals "Noooo!", template, [theBoolean:false]

        messageSource.addMessage("boolean.true",request.locale, "Yippeee!")
        messageSource.addMessage("boolean.false",request.locale, "Urghh!")

        assertOutputEquals "Yippeee!", template, [theBoolean:true]
        assertOutputEquals "Urghh!", template, [theBoolean:false]

        template = '<g:formatBoolean boolean="${somebool}" true="X" false=""/>'
        assertOutputEquals 'X', template, [somebool: true]
        assertOutputEquals '', template, [somebool: false]

        template = '<g:formatBoolean boolean="${somebool}" true="" false="X"/>'
        assertOutputEquals '', template, [somebool: true]
        assertOutputEquals 'X', template, [somebool: false]
    }

    @Issue('https://github.com/grails/grails-core/issues/9159')
    @Test
    void testFormatDateWithStreamCharBufferFormat() {
        def calender = new GregorianCalendar(1980,1,3)
        def format = 'yyyy-MM-dd'
        def buffer = new StreamCharBuffer()
        buffer.writer.append(format)
        def template = '<g:formatDate format="${format}" date="${date}"/>'
        assertOutputEquals("1980-02-03", template, [date:calender.getTime(), format: buffer])
    }

    @Test
    void testFormatDate() {
        def calender = new GregorianCalendar(1980,1,3)
        def template = '<g:formatDate format="yyyy-MM-dd" date="${date}"/>'
        assertOutputEquals("1980-02-03", template, [date:calender.getTime()])
    }

    /*
    void testFormatLocalDate() {
        def date = LocalDate.of(1980, 2, 3)
        def template = '<g:formatDate format="yyyy-MM-dd" date="${date}"/>'
        assertOutputEquals("1980-02-03", template, [date: date])
    }

    void testFormatLocalTime() {
        def date = LocalTime.of(22, 2, 3)
        def template = '<g:formatDate format="HH:mm:ss" date="${date}"/>'
        assertOutputEquals("22:02:03", template, [date: date])
    }

    void testFormatLocalDateTime() {
        def date = LocalDateTime.of(1980, 2, 3, 22, 2, 3)
        def template = '<g:formatDate format="yyyy-MM-dd HH:mm:ss" date="${date}"/>'
        assertOutputEquals("1980-02-03 22:02:03", template, [date: date])
    }

    void testFormatOffsetTime() {
        def date = OffsetTime.of(22, 2, 3, 0, ZoneOffset.MIN)
        def template = '<g:formatDate format="HH:mm:ss:SS" date="${date}"/>'
        assertOutputEquals("22:02:03:00", template, [date: date])
    }

    void testFormatOffsetDateTime() {
        def date = OffsetDateTime.of(1980, 2, 3, 22, 2, 3, 0, ZoneOffset.MIN)
        def template = '<g:formatDate format="yyyy-MM-dd HH:mm:ss:SS" date="${date}"/>'
        assertOutputEquals("1980-02-04 11:02:03:00", template, [date: date])
    }

    void testFormatZonedDateTime() {
        def date = ZonedDateTime.of(1980, 2, 3, 22, 2, 3, 0, ZoneOffset.MIN)
        def template = '<g:formatDate format="yyyy-MM-dd HH:mm:ss:SS" date="${date}"/>'
        assertOutputEquals("1980-02-04 11:02:03:00", template, [date: date])
    }

    void testFormatDateWithStyle() {
        def calender = new GregorianCalendar(1980,1,3)
        def template = '<g:formatDate date="${date}" type="date" style="LONG" locale="en_US"/>'
        assertOutputEquals("February 3, 1980", template, [date:calender.getTime()])
    }

    void testFormatLocalDateWithStyle() {
        def date = LocalDate.of(1980, 2, 3)
        def template = '<g:formatDate date="${date}" type="date" style="LONG" locale="en_US"/>'
        assertOutputEquals("February 3, 1980", template, [date: date])
    }

    void testFormatLocalDateTimeWithStyle() {
        def date = LocalDateTime.of(1980, 2, 3, 22, 2, 3)
        def template = '<g:formatDate date="${date}" type="date" style="LONG" locale="en_US"/>'
        assertOutputEquals("February 3, 1980", template, [date: date])
    }

    void testFormatOffsetDateTimeWithStyle() {
        def date = OffsetDateTime.of(1980, 2, 3, 22, 2, 3, 0, ZoneOffset.MIN)
        def template = '<g:formatDate date="${date}" type="date" style="LONG" locale="en_US"/>'
        assertOutputEquals("February 4, 1980", template, [date: date])
    }

    void testFormatZonedDateTimeWithStyle() {
        def date = ZonedDateTime.of(1980, 2, 3, 22, 2, 3, 0, ZoneOffset.MIN)
        def template = '<g:formatDate date="${date}" type="date" style="LONG" locale="en_US"/>'
        assertOutputEquals("February 4, 1980", template, [date: date])
    }

    void testFormatDateTimeWithStyle() {
        def calender = new GregorianCalendar(1980,1,3)
        def template = '<g:formatDate date="${date}" type="datetime" style="LONG" timeStyle="SHORT" locale="en_US"/>'
        assertOutputEquals("February 3, 1980 12:00 AM", template, [date:calender.getTime()])
    }

    void testFormatLocalDateWithTimeStyle() {
        def date = LocalDate.of(1980, 2, 3)
        def template = '<g:formatDate date="${date}" type="datetime" style="LONG" timeStyle="SHORT" locale="en_US"/>'
        assertOutputEquals("February 3, 1980 12:00 AM", template, [date: date])
    }

    void testFormatLocalDateTimeWithTimeStyle() {
        def date = LocalDateTime.of(1980, 2, 3, 22, 2, 3)
        def template = '<g:formatDate date="${date}" type="datetime" style="LONG" timeStyle="SHORT" locale="en_US"/>'
        assertOutputEquals("February 3, 1980 10:02 PM", template, [date: date])
    }

    void testFormatOffsetDateTimeWithTimeStyle() {
        def date = OffsetDateTime.of(1980, 2, 3, 22, 2, 3, 0, ZoneOffset.MIN)
        def template = '<g:formatDate date="${date}" type="datetime" style="LONG" timeStyle="SHORT" locale="en_US"/>'
        assertOutputEquals("February 4, 1980 11:02 AM", template, [date: date])
    }

    void testFormatZonedDateTimeWithTimeStyle() {
        def date = ZonedDateTime.of(1980, 2, 3, 22, 2, 3, 0, ZoneOffset.MIN)
        def template = '<g:formatDate date="${date}" type="datetime" style="LONG" timeStyle="SHORT" locale="en_US"/>'
        assertOutputEquals("February 4, 1980 11:02 AM", template, [date: date])
    }*/

    @Test
    void testFormatDateNullDate() {
        def template = '<g:formatDate format="yyyy-MM-dd" date="${date}"/>'
        assertOutputEquals("", template, [date:null])
    }

    @Test
    void testFormatDateCurrentDate() {
        def template = '<g:formatDate format="yyyy-MM-dd"/>'
        def output = applyTemplate(template)
        assertTrue(output ==~ /\d{4}-\d{2}-\d{2}/)
    }

    @Test
    void testFormatNumber() {
        def template = '<g:formatNumber number="${myNumber}" format="\\$###,##0"/>'
        assertOutputEquals('$10', template, [myNumber:10])
    }

    @Test
    void testFormatNumberNullNumber() {
        def template = '<g:formatNumber number="${myNumber}"/>'
        assertOutputEquals("", template, [myNumber:null])
    }

    @Test
    void testFormatNumberNoNumber() {
        assertThrows(GrailsTagException) {
            applyTemplate('<g:formatNumber/>')
        }
    }

    @Test
    void testFormatDateFromBundle() {
        def calender = new GregorianCalendar(1980,1,3)
        def template = '<g:formatDate formatName="format.date" date="${date}"/>'
        messageSource.addMessage("format.date", request.locale, "yyyy-MM-dd")

        assertOutputEquals("1980-02-03", template, [date:calender.getTime()])
    }

    @Test
    void testFormatNumberFromBundle() {
       def template = '<g:formatNumber number="${myNumber}" formatName="format.number" />'
       messageSource.addMessage("format.number", request.locale, '\$###,##0')
        assertOutputEquals('$10', template, [myNumber:10])
    }

    @Test
    void testEncodeAs() {
        def template = '<g:encodeAs codec="HTML">Coheed & Cambria</g:encodeAs>'
        assertOutputEquals('Coheed &amp; Cambria', template, [:])
    }

    @Test
    void testFormatBigDecimal() {
        def number = "3.12325678" as BigDecimal
        def template = '<g:formatNumber format="#.####" number="${number}"/>'
        assertOutputEquals("3.1233", template, [number: number])
    }

    @Test
    void testFormatCurrencyWithCode() {
        def number = "3.12325678" as BigDecimal
        def template = '<g:formatNumber type="currency" currencyCode="USD" number="${number}" locale="en_US" />'
        assertOutputEquals("\$3.12", template, [number: number])
    }

    @Test
    void testFormatNumberDecimals() {
        def number = "3.12325678" as BigDecimal
        def template = '<g:formatNumber type="number" number="${number}" locale="fi_FI" minFractionDigits="3" maxFractionDigits="3" />'
        assertOutputEquals("3,123", template, [number: number])
    }

    @Test
    void testFormatNumberRoundingModeHalfDown() {
        def number = "3.125" as BigDecimal
        def template = '<g:formatNumber type="number" number="${number}" locale="fi_FI" maxFractionDigits="2" roundingMode="HALF_DOWN" />'
        assertOutputEquals("3,12", template, [number: number])
    }

    @Test
    void testFormatNumberRoundingModeHalfUp() {
        def number = "3.125" as BigDecimal
        def template = '<g:formatNumber type="number" number="${number}" locale="fi_FI" maxFractionDigits="2" roundingMode="HALF_UP" />'
        assertOutputEquals("3,13", template, [number: number])
    }

    @Test
    void testFormatNumberRoundingModeUnnecessary() {
        def number = "3.125" as BigDecimal
        def template = '<g:formatNumber type="number" number="${number}" locale="fi_FI" maxFractionDigits="2" roundingMode="UNNECESSARY" />'
        assertOutputEquals("3,125", template, [number: number])
    }

    @Test
    void testFormatNumberRoundingModeUnnecessary2() {
        def number = "3.125" as BigDecimal
        def template = '<g:formatNumber type="number" number="${number}" locale="fi_FI" maxFractionDigits="3" roundingMode="UNNECESSARY" />'
        assertOutputEquals("3,125", template, [number: number])
    }

    @Test
    void testFormatNumberInteger() {
        def number = "3.12325678" as BigDecimal
        def template = '<g:formatNumber type="number" number="${number}" locale="fi_FI" minIntegerDigits="3" maxIntegerDigits="3" minFractionDigits="0" maxFractionDigits="0"/>'
        assertOutputEquals("003", template, [number: number])
    }

    @Test
    void testFormatNumberInteger2() {
        def number = 1
        def template = '<g:formatNumber type="number" number="${number}" minIntegerDigits="3"/>'
        assertOutputEquals("001", template, [number: number])
    }

    @Test
    void testFormatNumberIntegerWithNoGrouping() {
        def number = 1234
        def template = '<g:formatNumber type="number" number="${number}" minIntegerDigits="3" groupingUsed="false" locale="en_US"/>'
        assertOutputEquals("1234", template, [number: number])
        template = '<g:formatNumber type="number" number="${number}" minIntegerDigits="3" groupingUsed="${false}" locale="en_US"/>'
        assertOutputEquals("1234", template, [number: number])    }

    @Test
    void testFormatNumberIntegerWithGrouping() {
        def number = 1234
        def template = '<g:formatNumber type="number" number="${number}" minIntegerDigits="3" groupingUsed="true" locale="en_US"/>'
        assertOutputEquals("1,234", template, [number: number])
        template = '<g:formatNumber type="number" number="${number}" minIntegerDigits="3" groupingUsed="${true}" locale="en_US"/>'
        assertOutputEquals("1,234", template, [number: number])
    }

    @Test
    void testFormatNumberParsingString() {
        def number = "3,12325678"
        def template = '<g:formatNumber type="number" number="${number}" locale="fi_FI" minFractionDigits="3" maxFractionDigits="3" />'
        assertOutputEquals("3,123", template, [number: number])
    }

    @Test
    void testFormatNumberNaN() {
        def number = Double.NaN
        def locale = new Locale('en', 'US')
        def template = '<g:formatNumber number="${number}" locale="${locale}"/>'
        assertOutputEquals(new DecimalFormatSymbols(locale).getNaN(), template, [number: number, locale: locale])
    }

    @Test
    void testFormatNumberNaNCustomized() {
        def number = Double.NaN
        def template = '<g:formatNumber number="${number}" nan="n/a"/>'
        assertOutputEquals("n/a", template, [number: number])
    }
}

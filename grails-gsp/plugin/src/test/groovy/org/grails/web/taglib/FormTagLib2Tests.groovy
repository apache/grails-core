package org.grails.web.taglib


import org.junit.jupiter.api.Test
import org.w3c.dom.Document

import java.text.DateFormat

import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertThrows

/**
 * Tests for the FormTagLib.groovy file which contains tags to help with the
 * creation of HTML forms
 *
 * @author Graeme
 */
class FormTagLib2Tests extends AbstractGrailsTagTests {

    /** The name used for the datePicker tags created in the test cases. */
    private static final String DATE_PICKER_TAG_NAME = "testDatePicker"
    private static final def SELECT_TAG_NAME = "testSelect"

    private static final Collection DATE_PRECISIONS_INCLUDING_MINUTE = ["minute", null].asImmutable()
    private static final Collection DATE_PRECISIONS_INCLUDING_HOUR = ["hour", "minute", null].asImmutable()
    private static final Collection DATE_PRECISIONS_INCLUDING_DAY = ["day", "hour", "minute", null].asImmutable()
    private static final Collection DATE_PRECISIONS_INCLUDING_MONTH = ["month", "day", "hour", "minute", null].asImmutable()

    @Test
    void testDatePickerTagWithDefaultDateAndPrecision() {
        testDatePickerTag(null, null)
    }

    @Test
    void testDatePickerTagWithYearPrecision() {
        testDatePickerTag(null, "year")
    }

    @Test
    void testDatePickerTagWithMonthPrecision() {
        testDatePickerTag(null, "month")
    }

    @Test
    void testDatePickerTagWithDayPrecision() {
        testDatePickerTag(null, "day")
    }

    @Test
    void testDatePickerTagWithHourPrecision() {
        testDatePickerTag(null, "hour")
    }

    @Test
    void testDatePickerTagWithMinutePrecision() {
        testDatePickerTag(null, "minute")
    }

    @Test
    void testDatePickerTagWithCustomDate() {
        testDatePickerTag(new Date(0), null)
    }
    /*
    void testDatePickerTagWithLocalDateTime() {
        testDatePickerTag(LocalDateTime.now(), null)
    }

    void testDatePickerTagWithLocalDate() {
        testDatePickerTag(LocalDate.now(), null)
    }

    void testDatePickerTagWithLocalTime() {
        try {
            testDatePickerTag(LocalTime.now(), null)
        } catch (e) {
            assert e instanceof DateTimeException
        }
    }

    void testDatePickerTagWithOffsetDateTime() {
        testDatePickerTag(OffsetDateTime.now(), null)
    }

    void testDatePickerTagWithOffsetTime() {
        try {
            testDatePickerTag(OffsetTime.now(), null)
        } catch (e) {
            assert e instanceof DateTimeException
        }

    }

    void testDatePickerTagWithZonedDateTime() {
        testDatePickerTag(ZonedDateTime.now(), null)
    }*/

    @Test
    void testDatePickerTagWithDefault() {
        def defaultDate = Calendar.getInstance()
        defaultDate.add(Calendar.DAY_OF_MONTH, 7)
        Document document = getDatePickerOutput(null, 'day', defaultDate.getTime())
        assertNotNull(document)

        assertSelectFieldPresentWithSelectedValue(document, DATE_PICKER_TAG_NAME + "_year",
                defaultDate.get(Calendar.YEAR).toString())
        assertSelectFieldPresentWithSelectedValue(document, DATE_PICKER_TAG_NAME + "_month",
                (defaultDate.get(Calendar.MONTH) + 1).toString())
        assertSelectFieldPresentWithSelectedValue(document, DATE_PICKER_TAG_NAME + "_day",
                defaultDate.get(Calendar.DAY_OF_MONTH).toString())
    }

    @Test
    void testDatePickerTagThrowsErrorWithInvalidDefault() {
        assertThrows(Exception) {
            getDatePickerOutput(null, 'day', new Integer())
        }
        DateFormat defaultFormat = DateFormat.getInstance()
        Document document = getDatePickerOutput(null, 'day', defaultFormat.format(new Date()))
        assertNotNull(document)
    }

    @Test
    void testDatePickerTagWithCustomDateAndPrecision() {
        testDatePickerTag(new Date(0), "day")
    }

    @Test
    void testDatePickerTagWithNoneValues() {
        Document document = getDatePickerOutput("none", "day", null)
        assertNotNull(document)

        // validate presence and structure of hidden date picker form field
        assertXPathExists(
                document,
                "//input[@name='" + DATE_PICKER_TAG_NAME + "' and @type='hidden' and @value='date.struct']")

        // validate id attributes
        assertXPathExists(
                document,
                "//select[@name='" + DATE_PICKER_TAG_NAME + "_day' and @id='" + DATE_PICKER_TAG_NAME + "_day']")

        assertXPathExists(
                document,
                "//select[@name='" + DATE_PICKER_TAG_NAME + "_month' and @id='" + DATE_PICKER_TAG_NAME + "_month']")

        assertXPathExists(
                document,
                "//select[@name='" + DATE_PICKER_TAG_NAME + "_year' and @id='" + DATE_PICKER_TAG_NAME + "_year']")

        assertSelectFieldPresentWithSelectedValue(document, DATE_PICKER_TAG_NAME + "_year", '')
        assertSelectFieldPresentWithSelectedValue(document, DATE_PICKER_TAG_NAME + "_month", '')
        assertSelectFieldPresentWithSelectedValue(document, DATE_PICKER_TAG_NAME + "_day", '')
    }

    private void testDatePickerTag(Object date, String precision) {
        Document document = getDatePickerOutput(date, precision, null)
        assertNotNull(document)

        // validate presence and structure of hidden date picker form field
        assertXPathExists(
                document,
                "//input[@name='" + DATE_PICKER_TAG_NAME + "' and @type='hidden' and @value='date.struct']")

        // if no date was given, default to the current date
        Calendar calendar = new GregorianCalendar()
        if (date != null) {
            if (date instanceof Date) {
                calendar.setTime(date)
            } /*else if (date instanceof TemporalAccessor) {
                ZonedDateTime zonedDateTime
                if (date instanceof LocalDateTime) {
                    zonedDateTime = ZonedDateTime.of(date, ZoneId.systemDefault())
                } else if (date instanceof LocalDate) {
                    zonedDateTime = ZonedDateTime.of(date, LocalTime.MIN, ZoneId.systemDefault())
                } else {
                    zonedDateTime = ZonedDateTime.from(date)
                }
                calendar = GregorianCalendar.from(zonedDateTime)
            }*/
        }

        // validate id attributes
        String xp
        if (['day', 'hour', 'minute'].contains(precision)) {
            assertXPathExists(
                    document,
                    "//select[@name='" + DATE_PICKER_TAG_NAME + "_day' and @id='" + DATE_PICKER_TAG_NAME + "_day']")
        }

        if (['month', 'day', 'hour', 'minute'].contains(precision)) {
            assertXPathExists(
                    document,
                    "//select[@name='" + DATE_PICKER_TAG_NAME + "_month' and @id='" + DATE_PICKER_TAG_NAME + "_month']")
        }

        if (['minute', 'hour', 'day', 'month', 'year'].contains(precision)) {
            assertXPathExists(
                    document,
                    "//select[@name='" + DATE_PICKER_TAG_NAME + "_year' and @id='" + DATE_PICKER_TAG_NAME + "_year']")
        }

        if (['hour', 'minute'].contains(precision)) {
            assertXPathExists(
                    document,
                    "//select[@name='" + DATE_PICKER_TAG_NAME + "_hour' and @id='" + DATE_PICKER_TAG_NAME + "_hour']")
        }

        if ('minute' == precision) {
            assertXPathExists(
                    document,
                    "//select[@name='" + DATE_PICKER_TAG_NAME + "_minute' and @id='" + DATE_PICKER_TAG_NAME + "_minute']")
        }

        // validate presence and value of selected date fields
        validateSelectedYearValue(document, calendar)
        validateSelectedMonthValue(document, calendar, precision)
        validateSelectedDayValue(document, calendar, precision)
        validateSelectedHourValue(document, calendar, precision)
        validateSelectedMinuteValue(document, calendar, precision)
    }

    private void validateSelectedYearValue(Document document, Calendar calendar) {
        assertSelectFieldPresentWithSelectedValue(document, DATE_PICKER_TAG_NAME + "_year", Integer.toString(calendar.get(Calendar.YEAR)))
    }

    private void validateSelectedMonthValue(Document document, Calendar calendar, String precision) {
        final String FIELD_NAME = DATE_PICKER_TAG_NAME + "_month"

        String expectedMonthValue = Integer.toString(1); // January

        if (DATE_PRECISIONS_INCLUDING_MONTH.contains(precision)) {
            expectedMonthValue = Integer.toString(calendar.get(Calendar.MONTH) + 1)
            assertSelectFieldPresentWithSelectedValue(document, FIELD_NAME, expectedMonthValue)
        }
        else {
            assertSelectFieldNotPresent(document, FIELD_NAME)
        }
    }

    private void validateSelectedDayValue(Document document, Calendar calendar, String precision) {
        final String FIELD_NAME = DATE_PICKER_TAG_NAME + "_day"

        String expectedDayValue = Integer.toString(1); // 1st day of the month
        if (DATE_PRECISIONS_INCLUDING_DAY.contains(precision)) {
            expectedDayValue = Integer.toString(calendar.get(Calendar.DAY_OF_MONTH))
            assertSelectFieldPresentWithSelectedValue(document, FIELD_NAME, expectedDayValue)
        }
        else {
            assertSelectFieldNotPresent(document, FIELD_NAME)
        }
    }

    private void validateSelectedHourValue(Document document, Calendar calendar, String precision) {
        final String FIELD_NAME = DATE_PICKER_TAG_NAME + "_hour"

        String expectedHourValue = "00"
        if (DATE_PRECISIONS_INCLUDING_HOUR.contains(precision)) {
            int rawHourValue = calendar.get(Calendar.HOUR_OF_DAY)
            expectedHourValue = (rawHourValue < 10) ? ("0" + rawHourValue) : Integer.toString(rawHourValue)
            assertSelectFieldPresentWithSelectedValue(document, FIELD_NAME, expectedHourValue)
        }
        else {
            assertSelectFieldNotPresent(document, FIELD_NAME)
        }
    }

    private void validateSelectedMinuteValue(Document document, Calendar calendar, String precision) {

        if (precision == null) {
            return
        }

        final String FIELD_NAME = DATE_PICKER_TAG_NAME + "_minute"

        String expectedMinuteValue = "00"
        if (DATE_PRECISIONS_INCLUDING_MINUTE.contains(precision)) {
            int rawMinuteValue = calendar.get(Calendar.MINUTE)
            expectedMinuteValue = (rawMinuteValue < 10) ? ("0" + rawMinuteValue) : Integer.toString(rawMinuteValue)
            assertSelectFieldPresentWithSelectedValue(document, FIELD_NAME, expectedMinuteValue)
        }
        else {
            assertSelectFieldNotPresent(document, FIELD_NAME)
        }
    }

    private void assertSelectFieldPresentWithSelectedValue(Document document, String fieldName, String value) {
        assertXPathExists(
                document,
                "//select[@name='" + fieldName + "']/option[@selected='selected' and @value='" + value + "']")
    }

    private void assertSelectFieldPresentWithValue(Document document, String fieldName, String value) {
        assertXPathExists(
                document,
                "//select[@name='" + fieldName + "']/option[@value='" + value + "']")
    }

    private void assertSelectFieldPresentWithValueAndText(Document document, String fieldName, String value, String label) {
        assertXPathExists(
                document,
                "//select[@name='" + fieldName + "']/option[@value='" + value + "' and text()='" + label + "']")
    }

    private void assertSelectFieldNotPresent(Document document, String fieldName) {
        assertXPathNotExists(
                document,
                "//select[@name='" + fieldName + "']")
    }

    private void assertSelectPresent(Document document, String fieldName) {
        assertXPathExists(
                document,
                "//select[@name='" + fieldName + "']")
    }

    private Document getDatePickerOutput(value, precision, xdefault) {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)

        withTag("datePicker", pw) {tag ->

            assertNotNull(tag)

            Map attrs = new HashMap()
            attrs.put("name", DATE_PICKER_TAG_NAME)

            if (value != null) {
                attrs.value = value
            }

            if (xdefault != null) {
                attrs['default'] = xdefault
            }

            if (precision != null) {
                attrs.precision = precision
            }

            attrs.noSelection = ['': 'Please choose']
            tag.call(attrs)
        }
        String enclosed = "<test>" + sw.toString() + "</test>"
        return parseText(enclosed)
    }
}

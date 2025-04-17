package org.grails.web.taglib

import org.grails.plugins.web.taglib.FormTagLib
import org.grails.taglib.GrailsTagException
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue


/**
 * Tests for the FormTagLib.groovy file which contains tags to help with the
 * creation of HTML forms.
 *
 * @author Graeme
 */
class FormTagLib3Tests extends AbstractGrailsTagTests {

    /** The name used for the datePicker tags created in the test cases. */
    private static final String DATE_PICKER_TAG_NAME = "testDatePicker"

    private static final Collection DATE_PRECISIONS_INCLUDING_MINUTE = Collections.unmodifiableCollection(Arrays.asList(["minute", null] as String[]))
    private static final Collection DATE_PRECISIONS_INCLUDING_HOUR = Collections.unmodifiableCollection(Arrays.asList(["hour", "minute",null] as String[]))
    private static final Collection DATE_PRECISIONS_INCLUDING_DAY = Collections.unmodifiableCollection(Arrays.asList(["day", "hour", "minute", null] as String[]))
    private static final Collection DATE_PRECISIONS_INCLUDING_MONTH = Collections.unmodifiableCollection(Arrays.asList(["month", "day", "hour", "minute", null] as String[]))

    def lineSep = new String([(char)13,(char)10] as char[])

    @Test
    void testHiddenFieldTag() {
        final StringWriter sw = new StringWriter()
        final PrintWriter pw = new PrintWriter(sw)

        withTag("hiddenField", pw) { tag ->
            def attributes = [name: "testField", value: "1"]
            tag.call(attributes)

        }
        assertEquals '<input type="hidden" name="testField" value="1" id="testField" />', sw.toString()
    }

    @Test
    void testRadioTag() {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)

        withTag("radio", pw) { tag ->
            // use sorted map to be able to predict the order in which tag attributes are generated
            def attributes = new TreeMap([name: "testRadio", checked: "true", value: "1"])
            tag.call(attributes)

        }
        assertEquals "<input type=\"radio\" name=\"testRadio\" checked=\"checked\" value=\"1\" id=\"testRadio\"  />", sw.toString()

        sw = new StringWriter()
        pw = new PrintWriter(sw)

        withTag("radio", pw) { tag ->
            // use sorted map to be able to predict the order in which tag attributes are generated
            def attributes = new TreeMap([name: "testRadio", value: "2"])
            tag.call(attributes)

        }
        assertEquals "<input type=\"radio\" name=\"testRadio\" value=\"2\" id=\"testRadio\"  />", sw.toString()
    }

    @Test
    void testRadioUsesExpressionForDisable() {
        def template = '<g:set var="flag" value="${true}"/><g:radio disabled="${flag}" name="foo" value="bar" />'
        assertOutputContains('disabled="disabled"', template)
        template = '<g:set var="flag" value="${false}"/><g:radio disabled="${flag}" name="foo" value="bar" />'
        assertOutputContains('<input type="radio" name="foo" value="bar"', template)
        template = '<g:radio disabled="true" name="foo" value="bar" />'
        assertOutputContains('disabled="disabled"', template)
        template = '<g:radio disabled="false" name="foo" value="bar" />'
        assertOutputContains('<input type="radio" name="foo" value="bar"', template)
    }

    @Test
    void testRadioGroupTagWithLabels() {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)
        withTag("radioGroup", pw) { tag ->
            def attributes = new TreeMap([name: "testRadio", labels:['radio.1', 'radio.2', 'radio.3'], values:[1,2,3], value: "1"])
            tag.call(attributes, {"<p><g:message code=\"${it.label}\" /> ${it.radio}</p>"})
        }
        assertEquals ("<p><g:message code=\"radio.1\" /> <input type=\"radio\" name=\"testRadio\" checked=\"checked\" value=\"1\" /></p>"
                + lineSep + "<p><g:message code=\"radio.2\" /> <input type=\"radio\" name=\"testRadio\" value=\"2\" /></p>"
                + lineSep + "<p><g:message code=\"radio.3\" /> <input type=\"radio\" name=\"testRadio\" value=\"3\" /></p>"
                + lineSep , sw.toString())
    }

    @Test
    void testRadioGroupTagWithHtmlCodec() {
        withConfig('''
grails {
    views {
        gsp {
            codecs {
                expression = 'html'
                scriptlet = 'html'
                taglib = 'none'
                staticparts = 'none'
            }
        }
    }
}
''') {
            testRadioGroupTag()
        }
    }

    @Test
    void testRadioGroupTagWithNoneCodec() {
        withConfig('''
grails {
    views {
        gsp {
            codecs {
                expression = 'none'
                scriptlet = 'none'
                taglib = 'none'
                staticparts = 'none'
            }
        }
    }
}
''') {
            testRadioGroupTag()
        }
    }

    @Test
    void testRadioGroupTag() {
        def template='''<g:radioGroup name="myGroup" values="[1,2,3]" value="1" >${it.label} ${it.radio}</g:radioGroup>'''
        def expected='''Radio 1 <input type="radio" name="myGroup" checked="checked" value="1" />
Radio 2 <input type="radio" name="myGroup" value="2" />
Radio 3 <input type="radio" name="myGroup" value="3" />
'''
        assertOutputEquals(expected, template, [:], { it.replaceAll('\r\n','\n') })
    }

    @Test
    void testRadioGroupTagWithoutLabelsAndInvalidValue() {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)
        withTag("radioGroup", pw) { tag ->
            def attributes = new TreeMap([name: "testRadio2", values:[3,2], value: "1"])
            tag.call(attributes, {"<p><g:message code=\"${it.label}\" /> ${it.radio}</p>"})
        }
        assertEquals ("<p><g:message code=\"Radio 3\" /> <input type=\"radio\" name=\"testRadio2\" value=\"3\" /></p>"
                + lineSep + "<p><g:message code=\"Radio 2\" /> <input type=\"radio\" name=\"testRadio2\" value=\"2\" /></p>"
                + lineSep , sw.toString())
    }

    @Test
    void testRadioGroupTagWithNonStringValue() {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)
        withTag("radioGroup", pw) { tag ->
            def attributes = new TreeMap([name: "testRadio2", values:[4,1], value: 1])
            tag.call(attributes, {"<p><g:message code=\"${it.label}\" /> ${it.radio}</p>"})
        }
        assertEquals ("<p><g:message code=\"Radio 4\" /> <input type=\"radio\" name=\"testRadio2\" value=\"4\" /></p>"
                + lineSep + "<p><g:message code=\"Radio 1\" /> <input type=\"radio\" name=\"testRadio2\" checked=\"checked\" value=\"1\" /></p>"
                + lineSep , sw.toString())
    }

    @Test
    void testRadioGroupTagWithNoValue() {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)
        withTag("radioGroup", pw) { tag ->
            def attributes = new TreeMap([name: "testRadio2", values:[4,1]])
            tag.call(attributes, {"<p><g:message code=\"${it.label}\" /> ${it.radio}</p>"})
        }
        assertEquals ("<p><g:message code=\"Radio 4\" /> <input type=\"radio\" name=\"testRadio2\" value=\"4\" /></p>"
                + lineSep + "<p><g:message code=\"Radio 1\" /> <input type=\"radio\" name=\"testRadio2\" value=\"1\" /></p>"
                + lineSep , sw.toString())
    }

    @Test
    void testRadioGroupTagWithCustomAttributes() {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)
        withTag("radioGroup", pw) { tag ->
            def attributes = new TreeMap([name: "testRadio2", values:[4,1], onclick: "growl();"])
            tag.call(attributes, {"<p><g:message code=\"${it.label}\" /> ${it.radio}</p>"})
        }
        assertEquals ("<p><g:message code=\"Radio 4\" /> <input type=\"radio\" name=\"testRadio2\" value=\"4\" onclick=\"growl();\" /></p>"
                + lineSep + "<p><g:message code=\"Radio 1\" /> <input type=\"radio\" name=\"testRadio2\" value=\"1\" onclick=\"growl();\" /></p>"
                + lineSep , sw.toString())
    }

    @Test
    void testCheckboxTag() {
        def template = '<g:checkBox name="foo" value="${test}"/>'
        assertOutputEquals('<input type="hidden" name="_foo" /><input type="checkbox" name="foo" checked="checked" value="hello" id="foo"  />', template, [test:"hello"])

        template = '<g:checkBox name="foo" value="${test}" checked="false"/>'
        assertOutputEquals('<input type="hidden" name="_foo" /><input type="checkbox" name="foo" value="hello" id="foo"  />', template, [test:"hello"])

        template = '<g:checkBox name="foo" value="${test}" checked="${false}"/>'
        assertOutputEquals('<input type="hidden" name="_foo" /><input type="checkbox" name="foo" value="hello" id="foo"  />', template, [test:"hello"])

        template = '<g:checkBox name="foo" value="${test}" checked="${true}"/>'
        assertOutputEquals('<input type="hidden" name="_foo" /><input type="checkbox" name="foo" checked="checked" value="hello" id="foo"  />', template, [test:"hello"])

        template = '<g:checkBox name="foo" value="${test}" checked="true"/>'
        assertOutputEquals('<input type="hidden" name="_foo" /><input type="checkbox" name="foo" checked="checked" value="hello" id="foo"  />', template, [test:"hello"])

        template = '<g:checkBox name="foo." value="${test}" checked="true"/>'
        assertOutputEquals('<input type="hidden" name="foo._" /><input type="checkbox" name="foo." checked="checked" value="hello" id="foo."  />', template, [test:"hello"])
        
        template = '<g:checkBox name="foo.bar" value="${test}" checked="${true}"/>'
        assertOutputEquals('<input type="hidden" name="foo._bar" /><input type="checkbox" name="foo.bar" checked="checked" value="hello" id="foo.bar"  />', template, [test:"hello"])

        template = '<g:checkBox name="foo.bar" value="${test}" checked="${null}"/>'
        assertOutputEquals('<input type="hidden" name="foo._bar" /><input type="checkbox" name="foo.bar" value="hello" id="foo.bar"  />', template, [test:"hello"])

        template = '<g:checkBox name="foo.bar.bing.bang" value="${test}" checked="${null}"/>'
        assertOutputEquals('<input type="hidden" name="foo.bar.bing._bang" /><input type="checkbox" name="foo.bar.bing.bang" value="hello" id="foo.bar.bing.bang"  />', template, [test:"hello"])

        template = '<g:checkBox name="foo" value="${test}" form="myForm"/>'
        assertOutputEquals('<input type="hidden" name="_foo" form="myForm" /><input type="checkbox" name="foo" checked="checked" value="hello" form="myForm" id="foo"  />', template, [test:"hello"])
    }

    @Test
    void testCheckBoxUsesExpressionForDisable() {
        def template = '<g:set var="flag" value="${true}"/><g:checkBox disabled="${flag}" name="foo"/>'
        assertOutputContains('disabled="disabled"', template)
        template = '<g:set var="flag" value="${false}"/><g:checkBox disabled="${flag}" name="foo"/>'
        assertOutputContains('<input type="checkbox" name="foo" id="foo"', template)
        template = '<g:checkBox disabled="true" name="foo"/>'
        assertOutputContains('disabled="disabled"', template)
        template = '<g:checkBox disabled="false" name="foo"/>'
        assertOutputContains('<input type="checkbox" name="foo" id="foo"', template)
    }

    @Test
    void testRenderingNoSelectionOption() {
        final StringWriter sw = new StringWriter()
        final PrintWriter pw = new PrintWriter(sw)
        FormTagLib formTagLib = new FormTagLib()
        formTagLib.renderNoSelectionOptionImpl(pw, '', '', null)
        assertEquals '<option value=""></option>', sw.toString()
    }

    @Test
    void testCheckedOverridesValue() {
        def template = '<g:checkBox name="foo" value="${value}" checked="${checked}" />'
        assertOutputEquals '<input type="hidden" name="_foo" /><input type="checkbox" name="foo" checked="checked" value="0" id="foo"  />', template, [value: 0, checked: true]
        assertOutputEquals '<input type="hidden" name="_foo" /><input type="checkbox" name="foo" checked="checked" value="1" id="foo"  />', template, [value: 1, checked: true]
        assertOutputEquals '<input type="hidden" name="_foo" /><input type="checkbox" name="foo" value="0" id="foo"  />', template, [value: 0, checked: false]
        assertOutputEquals '<input type="hidden" name="_foo" /><input type="checkbox" name="foo" value="1" id="foo"  />', template, [value: 1, checked: false]
    }

    @Test
    void testNoHtmlEscapingTextAreaTag() {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)

        withTag("textArea", pw) { tag ->

            assertNotNull(tag)

            final Map attrs = new HashMap()
            attrs.put("name","testField")
            attrs.put("escapeHtml","false")
            attrs.put("value", "<b>some text</b>")

            tag.call(attrs, {})
        };

        { ->
            final String result = sw.toString()
            // need to inspect this as raw text so the DocumentHelper doesn't
            // unescape anything...
            assertTrue(result.indexOf("<b>some text</b>") >= 0)

            final Document document = parseText(sw.toString())
            assertNotNull(document)

            final Element inputElement = document.getDocumentElement()
            assertFalse inputElement.hasAttribute("escapeHtml") , "escapeHtml attribute should not exist"
        }()

        sw = new StringWriter()
        pw = new PrintWriter(sw)

        withTag("textArea", pw) { tag ->

            assertNotNull(tag)

            final Map attrs = new HashMap()
            attrs.put("name","testField")
            attrs.put("escapeHtml",false)
            attrs.put("value", "<b>some text</b>")

            tag.call(attrs, {})
        };
        {->
            final String result = sw.toString()
            // need to inspect this as raw text so the DocumentHelper doesn't
            // unescape anything...
            assertTrue(result.indexOf("<b>some text</b>") >= 0)

            final Document document = parseText(sw.toString())
            assertNotNull(document)

            final Element inputElement = document.getDocumentElement()
            assertFalse inputElement.hasAttribute("escapeHtml"), "escapeHtml attribute should not exist"
        }()
    }

    @Test
    void testHtmlEscapingTextAreaTag() {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)

        withTag("textArea", pw, 'g') { tag ->

            assertNotNull(tag)

            final Map attrs = new HashMap()
            attrs.put("name","testField")
            attrs.put("escapeHtml",true)
            attrs.put("value", "<b>some text</b>")

            tag.call(attrs, {})

        };
        { ->
            final String result = sw.toString()
            // need to inspect this as raw text so the DocumentHelper doesn't
            // unescape anything...
            assertTrue(result.indexOf("&lt;b&gt;some text&lt;/b&gt;") >= 0)

            final Document document = parseText(sw.toString())
            assertNotNull(document)

            final Element inputElement = document.getDocumentElement()
            assertFalse inputElement.hasAttribute("escapeHtml"), "escapeHtml attribute should not exist"
        }()

        sw = new StringWriter()
        pw = new PrintWriter(sw)

        withTag("textArea", pw, 'g') { tag ->

            assertNotNull(tag)

            final Map attrs = new HashMap()
            attrs.put("name","testField")
            attrs.put("escapeHtml","true")
            attrs.put("value", "<b>some text</b>")

            tag.call(attrs, {})
        };

        { ->
            final String result = sw.toString()
            // need to inspect this as raw text so the DocumentHelper doesn't
            // unescape anything...
            assertTrue(result.indexOf("&lt;b&gt;some text&lt;/b&gt;") >= 0)

            final Document document = parseText(sw.toString())
            assertNotNull(document)

            final Element inputElement = document.getDocumentElement()
            assertFalse inputElement.hasAttribute("escapeHtml"), "escapeHtml attribute should not exist"
        }()
    }

    @Test
    void testFieldTagWithEmptyNameAttribute() {
        final StringWriter sw = new StringWriter()
        final PrintWriter pw = new PrintWriter(sw)

        withTag("field", pw) { tag ->
            assertNotNull tag
            GrailsTagException thrown = assertThrows(GrailsTagException) {
                tag([name: ''])
            }
            assertEquals 'Tag [field] is missing required attribute [name] or [field]', thrown.message
        }
    }

    @Test
    void testSelectTagWithEmptyListFromAttribute() {
        final StringWriter sw = new StringWriter()
        final PrintWriter pw = new PrintWriter(sw)

        withTag("select", pw) { tag ->
            assertNotNull tag
            tag([name: 'mySelectTag', from: []])
        }

        assertTrue sw.toString().startsWith('<select name="mySelectTag" id="mySelectTag" >')
    }

    @Test
    void testSelectTagWithNoFromAttribute() {
        final StringWriter sw = new StringWriter()
        final PrintWriter pw = new PrintWriter(sw)

        withTag("select", pw) { tag ->
            assertNotNull tag
            GrailsTagException thrown = assertThrows(GrailsTagException) {
                tag([name: 'mySelectTag'])
            }
            assertEquals 'Tag [select] is missing required attribute [from]', thrown.message
        }
    }

    @Test
    void testSelectTagWithNoNameAttribute() {
        final StringWriter sw = new StringWriter()
        final PrintWriter pw = new PrintWriter(sw)

        withTag("select", pw) { tag ->
            assertNotNull tag
            GrailsTagException thrown = assertThrows(GrailsTagException) {
                tag([from: [1,2,3]])
            }
            assertEquals 'Tag [select] is missing required attribute [name]', thrown.message
        }
    }

    @Test
    void testSelectTagWithNullAttribute() {
        final StringWriter sw = new StringWriter()
        final PrintWriter pw = new PrintWriter(sw)

        withTag("select", pw) { tag ->
            assertNotNull tag
            tag([name: 'mySelectTag', from: [], errors: null])
        }

        println sw.toString()

        assertTrue sw.toString().startsWith('<select name="mySelectTag" id="mySelectTag" >')
    }

    @Test
    void testDatePickerWithYearsAndRelativeYearsAttribute() {
        final StringWriter sw = new StringWriter()
        final PrintWriter pw = new PrintWriter(sw)

        withTag("datePicker", pw) { tag ->
            GrailsTagException thrown = assertThrows(GrailsTagException) {
                tag(years:[1900..1910], relativeYears: [-2..10])
            }
            assertEquals "Tag [datePicker] does not allow both the years and relativeYears attributes to be used together.", thrown.message
        }
    }

    @Test
    void testDatePickerWithInvalidRelativeYears() {
        final StringWriter sw = new StringWriter()
        final PrintWriter pw = new PrintWriter(sw)

        withTag("datePicker", pw) { tag ->
            GrailsTagException thrown = assertThrows(GrailsTagException) {
                tag(relativeYears: 'not an integer range')
            }
            assertEquals "The [datePicker] relativeYears attribute must be a range of int.", thrown.message
        }
    }

    @Test
    void testDatePickerWithRelativeYearsAndReverseRange() {
        def now = Calendar.instance
        def currentYear = now.get(Calendar.YEAR)

        def template = '<g:datePicker relativeYears="[5..-2]"/>'
        def result = applyTemplate(template)

        assertEquals(-1, result.indexOf("""<option value="${currentYear - 4}">${currentYear - 4}</option>"""))
        assertEquals(-1, result.indexOf("""<option value="${currentYear - 3}">${currentYear - 3}</option>"""))

        def fiveDaysFromNowIndex = result.indexOf("""<option value="${currentYear + 5}">${currentYear + 5}</option>""")
        def fourDaysFromNowIndex = result.indexOf("""<option value="${currentYear + 4}">${currentYear + 4}</option>""")
        assertTrue fiveDaysFromNowIndex < fourDaysFromNowIndex

        def threeDaysFromNowIndex = result.indexOf("""<option value="${currentYear + 3}">${currentYear + 3}</option>""")
        assertTrue fourDaysFromNowIndex < threeDaysFromNowIndex

        def twoDaysFromNowIndex = result.indexOf("""<option value="${currentYear + 2}">${currentYear + 2}</option>""")
        assertTrue threeDaysFromNowIndex < twoDaysFromNowIndex

        def tomorrowIndex = result.indexOf("""<option value="${currentYear + 1}">${currentYear + 1}</option>""")
        assertTrue twoDaysFromNowIndex < tomorrowIndex

        def todayIndex = result.indexOf("""<option value="${currentYear}" selected="selected">${currentYear}</option>""")
        assertTrue tomorrowIndex < todayIndex

        def yesterdayIndex = result.indexOf("""<option value="${currentYear - 1}">${currentYear - 1}</option>""")
        assertTrue todayIndex < yesterdayIndex

        def twoDaysAgoIndex = result.indexOf("""<option value="${currentYear - 2}">${currentYear - 2}</option>""")
        assertTrue yesterdayIndex < twoDaysAgoIndex

        assertEquals(-1, result.indexOf("""<option value="${currentYear + 6}">${currentYear + 6}</option>"""))
        assertEquals(-1, result.indexOf("""<option value="${currentYear + 7}">${currentYear + 7}</option>"""))
    }

    @Test
    void testDatePickerWithRelativeYears() {
        def now = Calendar.instance
        def currentYear = now.get(Calendar.YEAR)

        def template = '<g:datePicker relativeYears="[-2..5]"/>'
        def result = applyTemplate(template)

        assertEquals(-1, result.indexOf("""<option value="${currentYear - 4}">${currentYear - 4}</option>"""))
        assertEquals(-1, result.indexOf("""<option value="${currentYear - 3}">${currentYear - 3}</option>"""))
        assertTrue result.contains("""<option value="${currentYear - 2}">${currentYear - 2}</option>""")
        assertTrue result.contains("""<option value="${currentYear - 1}">${currentYear - 1}</option>""")
        assertTrue result.contains("""<option value="${currentYear}" selected="selected">${currentYear}</option>""")
        assertTrue result.contains("""<option value="${currentYear + 1}">${currentYear + 1}</option>""")
        assertTrue result.contains("""<option value="${currentYear + 2}">${currentYear + 2}</option>""")
        assertTrue result.contains("""<option value="${currentYear + 3}">${currentYear + 3}</option>""")
        assertTrue result.contains("""<option value="${currentYear + 4}">${currentYear + 4}</option>""")
        assertTrue result.contains("""<option value="${currentYear + 5}">${currentYear + 5}</option>""")
        assertEquals(-1, result.indexOf("""<option value="${currentYear + 6}">${currentYear + 6}</option>"""))
        assertEquals(-1, result.indexOf("""<option value="${currentYear + 7}">${currentYear + 7}</option>"""))

        template = '<g:datePicker relativeYears="${-2..5}"/>'
        result = applyTemplate(template)

        assertEquals(-1, result.indexOf("""<option value="${currentYear - 4}">${currentYear - 4}</option>"""))
        assertEquals(-1, result.indexOf("""<option value="${currentYear - 3}">${currentYear - 3}</option>"""))
        assertTrue result.contains("""<option value="${currentYear - 2}">${currentYear - 2}</option>""")
        assertTrue result.contains("""<option value="${currentYear - 1}">${currentYear - 1}</option>""")
        assertTrue result.contains("""<option value="${currentYear}" selected="selected">${currentYear}</option>""")
        assertTrue result.contains("""<option value="${currentYear + 1}">${currentYear + 1}</option>""")
        assertTrue result.contains("""<option value="${currentYear + 2}">${currentYear + 2}</option>""")
        assertTrue result.contains("""<option value="${currentYear + 3}">${currentYear + 3}</option>""")
        assertTrue result.contains("""<option value="${currentYear + 4}">${currentYear + 4}</option>""")
        assertTrue result.contains("""<option value="${currentYear + 5}">${currentYear + 5}</option>""")
        assertEquals(-1, result.indexOf("""<option value="${currentYear + 6}">${currentYear + 6}</option>"""))
        assertEquals(-1, result.indexOf("""<option value="${currentYear + 7}">${currentYear + 7}</option>"""))
    }

    @Test
    void testDatePickerAriaLabel() {
        def template = '<g:datePicker name="myDate" value="${new Date()}"/>'
        def result = applyTemplate(template)

        assertTrue result.contains('<select name="myDate_year" id="myDate_year" aria-labelledby="myDate myDate_year"')
        assertTrue result.contains('<select name="myDate_month" id="myDate_month" aria-labelledby="myDate myDate_month"')
        assertTrue result.contains('<select name="myDate_day" id="myDate_day" aria-labelledby="myDate myDate_day"')
        assertTrue result.contains('<select name="myDate_hour" id="myDate_hour" aria-labelledby="myDate myDate_hour"')
        assertTrue result.contains('<select name="myDate_minute" id="myDate_minute" aria-labelledby="myDate myDate_minute"')
    }
}

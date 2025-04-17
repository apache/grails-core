package org.grails.web.taglib

import groovy.xml.XmlSlurper
import org.apache.commons.text.WordUtils
import org.junit.jupiter.api.Test
import org.springframework.context.MessageSourceResolvable
import org.springframework.web.servlet.support.RequestContextUtils as RCU
import org.w3c.dom.Document

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * @author Graeme Rocher
 * @since 1.0
 *
 * Created: Jan 25, 2008
 */
class SelectTagTests extends AbstractGrailsTagTests {

    private static final String SELECT_TAG_NAME = "testSelect"

    @Test
    void testSelectTagEscaping() {
        def template = '<g:select id="${foo}.genre" name="${foo}.genre" value="${book?.genre}" from="${[\'non-fiction\',\'fiction\']}" noSelection="[\'\':\'-Genre-\']" />'
        def result = applyTemplate(template, [foo:'bar" /><script>alert("gotcha")</script>'])

        assertTrue result.startsWith('<select id="bar&quot; /&gt;&lt;script&gt;alert(&quot;gotcha&quot;)&lt;/script&gt;.genre" name="bar&quot; /&gt;&lt;script&gt;alert(&quot;gotcha&quot;)&lt;/script&gt;.genre" >'), "should have HTML escaped attributes"
    }

    @Test
    void testSelectTagEscapingValue() {
        def template = '<g:select id="genre" name="genre" from="${values}" />'
        def result = applyTemplate(template, [values: ["\"></option></select><script>alert('hi')</script>"]])

        println result
        assertTrue result.contains('<option value="&quot;&gt;&lt;/option&gt;&lt;/select&gt;&lt;script&gt;alert(&#39;hi&#39;)&lt;/script&gt;" >&quot;&gt;&lt;/option&gt;&lt;/select&gt;&lt;script&gt;alert(&#39;hi&#39;)&lt;/script&gt;</option>'), "should have HTML escaped values"
    }

    @Test
    void testSelectUsesExpressionForDisable() {
        def template = '<g:set var="flag" value="${true}"/><g:select disabled="${flag}" name="foo" id="foo" from="[1,2,3]" />'
        assertOutputContains('disabled="disabled"', template)
        template = '<g:set var="flag" value="${false}"/><g:select disabled="${flag}" name="foo" id="foo" from="[1,2,3]" />'
        assertOutputContains('<select name="foo" id="foo" >', template)
        template = '<g:select disabled="true" name="foo" id="foo" from="[1,2,3]" />'
        assertOutputContains('disabled="disabled"', template)
        template = '<g:select disabled="false" name="foo" id="foo" from="[1,2,3]" />'
        assertOutputContains('<select name="foo" id="foo" >', template)
    }

    @Test
    void testSelectWithBigDecimal() {
        def template = '<g:set var="value" value="${2.4}"/><g:select name="foo" from="[1,2,3]" value="${value}" />'
        assertOutputContains('<option value="2" selected="selected" >2</option>', template)
    }

    @Test
    void testSimpleSelect() {
        def template = '<g:select name="foo" from="[1,2,3]" value="1" />'
        assertOutputContains('<option value="1" selected="selected" >1</option>', template)
        assertOutputContains('<option value="2" >2</option>', template)
        assertOutputContains('<option value="3" >3</option>', template)
    }

    @Test
    void testMultiSelect() {
        def template = '<g:select name="foo" from="[1,2,3]" value="[2,3]" />'

        assertOutputContains('<select name="foo" multiple="multiple" id="foo" >', template)
        assertOutputContains('<option value="1" >1</option>', template)
        assertOutputContains('<option value="2" selected="selected" >2</option>', template)
        assertOutputContains('<option value="3" selected="selected" >3</option>', template)
    }

    @Test
    void testSelectWithCustomOptionKeyAndValue() {

        def list = [new SelectTestObject(id:1L, name:"Foo"),new SelectTestObject(id:2L, name:"Bar")]

        def template = '<g:select optionKey="id" optionValue="name" name="foo" from="${objList}" value="2" />'
        assertOutputContains('<option value="2" selected="selected" >Bar</option>', template,[objList:list])
        assertOutputContains('<option value="1" >Foo</option>', template,[objList:list])
    }

    @Test
    void testMultiSelectWithCustomOptionKeyAndValue() {
        def list = [new SelectTestObject(id:1L, name:"Foo"),new SelectTestObject(id:2L, name:"Bar"),new SelectTestObject(id:3L, name:"More")]

        def template = '<g:select optionKey="id" optionValue="name" name="foo" from="${objList}" value="${[2L, 3L]}" />'
        assertOutputContains('<option value="2" selected="selected" >Bar</option>', template,[objList:list])
        assertOutputContains('<option value="1" >Foo</option>', template,[objList:list])
        assertOutputContains('<option value="3" selected="selected" >More</option>', template,[objList:list])
    }

    @Test
    void testSelectWithCustomOptionKeyAndValueAsClosure() {
        def list = [new SelectTestObject(id:1L, name:"Foo"),new SelectTestObject(id:2L, name:"Bar")]
        def template = '<g:select optionKey="id" optionValue="${{it.name?.toUpperCase()}}" name="foo" from="${objList}" value="2" />'

        printCompiledSource(template,[objList:list])
        assertOutputContains('<option value="2" selected="selected" >BAR</option>', template,[objList:list])
        assertOutputContains('<option value="1" >FOO</option>', template,[objList:list])
    }

    /**
     * Test case for GRAILS-3596: GString keys and string selected values
     * should match if they resolve to the same text.
     */
    @Test
    void testSelectWithGStringKeysAndStringValue() {
        def counter = 1
        def list = [
            "Item ${counter++}": "Item One",
            "Item ${counter++}": "Item Two",
            "Item ${counter++}": "Item Three" ]
        def template = '<g:select optionKey="key" optionValue="value" name="foo" from="${objList}" value="Item 2" />'

        printCompiledSource(template,[objList:list])
        assertOutputContains('<option value="Item 2" selected="selected" >Item Two</option>', template,[objList:list])
        assertOutputContains('<option value="Item 1" >Item One</option>', template,[objList:list])
    }

    /**
     * Test case for GRAILS-3596: GString selected values and string keys
     * should match if they resolve to the same text.
     */
    @Test
    void testSelectWithStringKeysAndGStringValue() {
        def counter = 3
        def list = [
            "Item 1": "Item One",
            "Item 2": "Item Two",
            "Item 3": "Item Three" ]
        def template = '<g:select optionKey="key" optionValue="value" name="foo" from="${objList}" value="${value}" />'

        printCompiledSource(template,[objList:list])
        assertOutputContains(
                '<option value="Item 3" selected="selected" >Item Three</option>',
                template,
                [objList: list, value: "Item $counter"])
        assertOutputContains('<option value="Item 2" >Item Two</option>', template,[objList:list, value:"Item $counter"])
    }

    @Test
    void testSelectTag() {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)

        def range = 1..10

        withTag("select", pw) { tag ->
            // use sorted map to be able to predict the order in which tag attributes are generated
            def attributes = new TreeMap([name: SELECT_TAG_NAME, from: range ])
            tag.call(attributes)
        }

        def doc = parseText(sw.toString())
        assertNotNull(doc)

        range.each() {
            assertSelectFieldPresentWithValue(doc, SELECT_TAG_NAME, it.toString())
        }

        sw = new StringWriter()
        pw = new PrintWriter(sw)

        def sel = '5'

        withTag("select", pw) { tag ->
            // use sorted map to be able to predict the order in which tag attributes are generated
            def attributes = new TreeMap([name: SELECT_TAG_NAME, value: sel, from: range ])
            tag.call(attributes)
        }

        doc = parseText(sw.toString())
        assertNotNull(doc)

        range.each() {
            if (it != sel) {
                assertSelectFieldPresentWithValue(doc, SELECT_TAG_NAME, it.toString())
            } else {
                assertSelectFieldPresentWithSelectedValue(doc, SELECT_TAG_NAME, it.toString())
            }
        }
    }

    @Test
    void testSelectTagWithNoSelectionSet() {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)

        def range = ['a', 'b', 'c', 'd', 'e']

        withTag("select", pw) { tag ->
            // use sorted map to be able to predict the order in which tag attributes are generated
            def attributes = new TreeMap([name: SELECT_TAG_NAME, noSelection:['?':'NONE'], from: range ])
            tag.call(attributes)
        }

        def xml = new XmlSlurper().parseText(sw.toString())

        assertEquals "testSelect", xml.@name?.toString()
        assertEquals "testSelect", xml.@id?.toString()
        assertEquals "NONE", xml.option[0].text()
        assertEquals "?", xml.option[0].@value.toString()

        range.eachWithIndex { e, i ->
            assertEquals e, xml.option[i+1].text()
            assertEquals e, xml.option[i+1].@value.toString()
        }

        sw = new StringWriter()
        pw = new PrintWriter(sw)

        withTag("select", pw) { tag ->
            // use sorted map to be able to predict the order in which tag attributes are generated
            def attributes = new TreeMap([name: SELECT_TAG_NAME, value: '', noSelection:['':'NONE'], from: range ])
            tag.call(attributes)
        }

        def doc = parseText(sw.toString())
        assertNotNull(doc)

        assertSelectFieldPresentWithSelectedValue(doc, SELECT_TAG_NAME, '')
        range.each() {
            assertSelectFieldPresentWithValue(doc, SELECT_TAG_NAME, it.toString())
        }
    }

    @Test
    void testSelectTagWithValueMessagePrefixSet() {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)

        def categoryMap = ['M':'Mystery' , 'T':'Thriller', 'F':'Fantasy']
        def categoryList = categoryMap.keySet()

        def valueMessagePrefix = "book.category"

        // test without messages set; value will be used as text

        withTag("select", pw) { tag ->
            // use sorted map to be able to predict the order in which tag attributes are generated
            def attributes = new TreeMap([name: SELECT_TAG_NAME, valueMessagePrefix: valueMessagePrefix, from: categoryList])
            tag.call(attributes)
        }

        def doc = parseText(sw.toString())
        assertNotNull(doc)

        // assert select field uses value for both the value as the text (as there is no text found within messages)
        categoryMap.each() { value, text ->
            assertSelectFieldPresentWithValueAndText(doc, SELECT_TAG_NAME, value, value)
        }

        // test with messages set

        categoryMap.each() { value, text ->
            messageSource.addMessage(valueMessagePrefix + "." + value, RCU.getLocale(request), text)
        }

        sw = new StringWriter()
        pw = new PrintWriter(sw)

        withTag("select", pw) { tag ->
            // use sorted map to be able to predict the order in which tag attributes are generated
            def attributes = new TreeMap([name: SELECT_TAG_NAME, valueMessagePrefix: valueMessagePrefix, from: categoryList])
            tag.call(attributes)
        }

        doc = parseText(sw.toString())
        assertNotNull(doc)

        // assert select field uses value and text
        categoryMap.each() { value, text ->
            assertSelectFieldPresentWithValueAndText(doc, SELECT_TAG_NAME, value, text)
        }
    }

    @Test
    void testMultipleSelect() {
        def categories = [
                new Expando(code: 'M', label: 'Mystery'),
                new Expando(code: 'T', label: 'Thriller'),
                new Expando(code: 'F', label: 'Fantasy'),
                new Expando(code: 'SF', label: 'Science Fiction'),
                new Expando(code: 'C', label: 'Crime') ]
        def selected = [ 'T', 'C']
        checkMultiSelect(categories, selected, {cat -> selected.contains(cat.code) })
    }

    @Test
    void testMultipleSelectWithObjectValues() {
        def sel1 = new Expando(code: 'T', label: 'Thriller'),
            sel2 = new Expando(code: 'C', label: 'Crime')
        def categories = [
                new Expando(code: 'M', label: 'Mystery'),
                sel1,
                new Expando(code: 'F', label: 'Fantasy'),
                new Expando(code: 'SF', label: 'Science Fiction'),
                sel2 ]
        def selected = [ sel1, sel2]
        checkMultiSelect(categories, selected, {cat -> selected.contains(cat) })
    }

    void checkMultiSelect(List categories, List selected, Closure isSelected) {
        final StringWriter sw = new StringWriter()
        final PrintWriter pw = new PrintWriter(sw)

        // Execute the tag.
        withTag("select", pw) { tag ->
            // use sorted map to be able to predict the order in which tag attributes are generated
            def attributes = new TreeMap(
                    name: SELECT_TAG_NAME,
                    from: categories,
                    value: selected,
                    optionKey: 'code',
                    optionValue: 'label')
            tag.call(attributes)
        }

        def doc = parseText(sw.toString())
        assertNotNull(doc)

        // Make sure that the "multiple" attribute is there.
        def value = xpath.evaluate("//select[@name='" + SELECT_TAG_NAME + "']/@multiple", doc)
        assertEquals("multiple", value)

        // assert select field uses value for both the value as the text (as there is no text found within messages)
        int actualSelected = 0
        categories.each() { cat ->
            if (isSelected.call(cat)) {
                assertSelectFieldPresentWithSelectedValueAndText(doc, SELECT_TAG_NAME, cat.code, cat.label)
                actualSelected++
            }
            else {
                assertSelectFieldPresentWithValueAndText(doc, SELECT_TAG_NAME, cat.code, cat.label)
            }
        }

        assertEquals(selected.size(), actualSelected, "expecting selected options")
    }

    @Test
    void testSelectFromListOfMessageSourceResolvableObjectsUsesDefaultMessage() {
        def list = Title.values()

        def template = '<g:select name="foo" from="${list}" value="MRS" />'
        assertOutputContains '<option value="MR" >Mr</option>', template, [list: list]
        assertOutputContains '<option value="MRS" selected="selected" >Mrs</option>', template, [list: list]
        assertOutputContains '<option value="MS" >Ms</option>', template, [list: list]
        assertOutputContains '<option value="DR" >Dr</option>', template, [list: list]
    }

    @Test
    void testSelectFromListOfMessageSourceResolvableObjectsUsesI18nProperty() {
        def list = Title.values()

        def locale = new Locale("af", "ZA")
        messageSource.addMessage("org.grails.web.taglib.Title.MR", locale, "Mnr")
        messageSource.addMessage("org.grails.web.taglib.Title.MRS", locale, "Mev")
        messageSource.addMessage("org.grails.web.taglib.Title.MS", locale, "Mej")

        webRequest.currentRequest.addPreferredLocale(locale)

        def template = '<g:select name="foo" from="${list}" value="MRS" />'
        assertOutputContains '<option value="MR" >Mnr</option>', template, [list: list]
        assertOutputContains '<option value="MRS" selected="selected" >Mev</option>', template, [list: list]
        assertOutputContains '<option value="MS" >Mej</option>', template, [list: list]
        assertOutputContains '<option value="DR" >Dr</option>', template, [list: list]
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
                "//select[@name='" + fieldName + "']/option[@value='" + value + "' and text()='"+label+"']")
    }

    private void assertSelectFieldPresentWithSelectedValueAndText(Document document, String fieldName, String value, String label) {
        assertXPathExists(
                document,
                "//select[@name='" + fieldName + "']/option[@selected='selected' and @value='" + value + "' and text()='"+label+"']")
    }

    private void assertSelectFieldNotPresent(Document document, String fieldName) {
        assertXPathNotExists(
                document,
                "//select[@name='" + fieldName + "']")
    }
}

class SelectTestObject {
    Long id
    String name
}

enum Title implements MessageSourceResolvable {
    MR, MRS, MS, DR

    String[] getCodes() {
        ["${getClass().name}.${name()}"] as String[]
    }

    Object[] getArguments() {
        [] as Object[]
    }

    String getDefaultMessage() {
        use(WordUtils) {
            name().toLowerCase().replaceAll(/_+/, " ").capitalizeFully()
        }
    }
}

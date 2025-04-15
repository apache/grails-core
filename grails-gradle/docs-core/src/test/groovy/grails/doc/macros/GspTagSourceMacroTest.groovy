/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.doc.macros

import spock.lang.Specification

/**
 * Tests for the GspTagSourceMacro class file which is used by the grails-doc
 * project to parse out TagLib closure methods and include the source in
 * the documentation of each tag.
 *
 * @author John Wagenleitner
 */
class GspTagSourceMacroTest extends Specification {

    void 'extracts tag closure source'() {
        given:
        GspTagSourceMacro macro = new GspTagSourceMacro([])
        String closureSource = macro.extractTagClosureSource(tagName, testSource)

        expect:
        closureSource.split('\n').size() == linesOfSource

        where:
        tagName               | linesOfSource
        'textField'           | 5
        'field'               | 4
        'checkBox'            | 60
        'uploadForm'          | 3
        'tagWithTypedAttrs'   | 3
    }

    void 'returns empty string if tag does not exist'() {
        given:
        GspTagSourceMacro macro = new GspTagSourceMacro([])

        when:
        String closureSource = macro.extractTagClosureSource('tagNotExists', testSource)

        then:
        '' == closureSource
    }

    void 'returns empty string if not source text'() {
        given:
        GspTagSourceMacro macro = new GspTagSourceMacro([])

        when:
        String closureSource = macro.extractTagClosureSource('someTag', '')

        then:
        '' == closureSource
    }

    static String testSource = '''
@TagLib
class TestTagLib implements TagLibrary {

    Closure textField = { attrs ->
        attrs.type = "text"
        attrs.tagName = "textField"
        fieldImpl(out, attrs)
    }

    def hiddenFieldImpl(out, attrs) {
        attrs.type = "hidden"
        attrs.tagName = "hiddenField"
        fieldImpl(out, attrs)
    }

    Closure field = { attrs ->
        attrs.tagName = "field"
        fieldImpl(out, attrs)
    }

    @CompileStatic
    private void outputNameAsIdIfIdDoesNotExist(Map attrs, GrailsPrintWriter out) {
        if (!attrs.containsKey('id') && attrs.containsKey('name')) {
            Encoder htmlEncoder = codecLookup?.lookupEncoder('HTML')
            out << 'id="\'
            out << (htmlEncoder != null ? htmlEncoder.encode(attrs.name) : attrs.name)
            out << '" \'
        }
    }

    Closure checkBox = { attrs ->
        def value = attrs.remove('value')
        def name = attrs.remove('name')
        booleanToAttribute(attrs, 'disabled')
        booleanToAttribute(attrs, 'readonly')

        // Deal with the "checked" attribute. If it doesn't exist, we
        // default to a value of "true", otherwise we use Groovy Truth
        // to determine whether the HTML attribute should be displayed or not.
        def checked = true
        def checkedAttributeWasSpecified = false
        if (attrs.containsKey('checked')) {
            checkedAttributeWasSpecified = true
            checked = attrs.remove('checked')
        }

        if (checked instanceof String) checked = Boolean.valueOf(checked)

        if (value == null) value = false
        def hiddenValue = ""

        def unprocessed = value
        value = processFormFieldValueIfNecessary(name, value,"checkbox")
        hiddenValue = processFormFieldValueIfNecessary("_${name}", hiddenValue, "hidden")

        def hiddenFieldName
        if(name.indexOf('.') == -1) {
            hiddenFieldName = "_${name}"
        } else {
            def lastDot = name.lastIndexOf('.')
            hiddenFieldName = name[0..lastDot] + '_' + name[(lastDot+1)..-1]
        }
        out << "<input type=\\"hidden\\" name=\\"${hiddenFieldName}\\""
        if (hiddenValue != "") {
            out << " value=\\"${hiddenValue}\\""
        }
        out << " /><input type=\\"checkbox\\" name=\\"${name}\\" "
        if (checkedAttributeWasSpecified) {
            if (checked) {
                out << 'checked="checked" \'
            }
        }
        else if (unprocessed) {
            out << 'checked="checked" \'
        }

        def outputValue = !(unprocessed instanceof Boolean || unprocessed?.getClass() == boolean)
        if (outputValue) {
            out << "value=\\"${value}\\" "
        }
        // process remaining attributes
        outputAttributes(attrs, out)

        if (!attrs.containsKey('id')) {
            out << """id="${name}" """
        }

        // close the tag, with no body
        out << ' />\'
    }

    private processFormFieldValueIfNecessary(name, value, type) {
        if (requestDataValueProcessor != null) {
            return requestDataValueProcessor.processFormFieldValue(request, name, "${value}", type)
        }
        return value
    }

    Closure uploadForm = { attrs, body ->
        attrs.enctype = "multipart/form-data"; out << form(attrs, body)
    }

    Closure tagWithTypedAttrs = { Map attrs, body ->
        out << 'some layout'
    }

}'''

}

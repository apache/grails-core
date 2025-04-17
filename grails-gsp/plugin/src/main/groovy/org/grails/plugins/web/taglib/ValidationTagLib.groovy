/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.plugins.web.taglib

import grails.artefact.TagLibrary
import grails.gsp.TagLib
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder
import org.apache.commons.text.StringEscapeUtils
import org.grails.encoder.CodecLookup
import org.grails.encoder.Encoder
import org.grails.taglib.GroovyPageAttributes
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.PropertyEditorRegistry
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceResolvable
import org.springframework.context.NoSuchMessageException
import org.springframework.context.support.DefaultMessageSourceResolvable
import org.springframework.validation.Errors

import java.beans.PropertyEditor
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * Tags to handle validation and errors.
 *
 * @author Graeme Rocher
 */
@TagLib
class ValidationTagLib implements TagLibrary {

    static returnObjectForTags = ['message', 'fieldError', 'formatValue']

    MessageSource messageSource
    CodecLookup codecLookup

    /**
     * Renders an error message for the given bean and field.<br/>
     *
     * eg. &lt;g:fieldError bean="${book}" field="title" /&gt;
     *
     * @attr bean REQUIRED The bean to check for errors
     * @attr field REQUIRED The field of the bean or model reference to check
     * @attr message The object to resolve the message for. Objects must implement org.springframework.context.MessageSourceResolvable.
     * @attr encodeAs The name of a codec to apply, i.e. HTML, JavaScript, URL etc
     * @attr locale override locale to use instead of the one detected
     */
    Closure fieldError = { attrs, body ->
        def bean = attrs.bean
        def field = attrs.field
        def encodeAs = attrs.encodeAs

        if (bean && field) {
            if (bean.metaClass.hasProperty(bean, 'errors')) {
                return messageImpl(error: bean.errors?.getFieldError(field), encodeAs: encodeAs ?: "HTML")
            }
        }

        return ''
    }

    /**
     * Obtains the value of a field either from the original errors.<br/>
     *
     * eg. &lt;g:fieldValue bean="${book}" field="title" /&gt;
     *
     * @emptyTag
     *
     * @attr bean REQUIRED The bean to check for errors
     * @attr field REQUIRED The field of the bean or model reference to check
     * @attr valueMessagePrefix Setting this allows the value to be resolved from the I18n messages.
     *
     */
    Closure fieldValue = { attrs, body ->
        def bean = attrs.bean
        String field = attrs.field?.toString()
        if (!bean || !field) {
            return
        }
        def valueMessagePrefix = attrs.valueMessagePrefix
        def valueMessage = (valueMessagePrefix) ?
            messageSource.getMessage("${valueMessagePrefix}.${field}", null, null, request.locale) :
            null

        def tagSyntaxCall = (attrs instanceof GroovyPageAttributes) ? attrs.isGspTagSyntaxCall() : false

        def rejectedValue = null
        if (valueMessage) {
            rejectedValue = valueMessage
        } else if (bean.metaClass.hasProperty(bean, 'errors')) {
            Errors errors = bean.errors
            rejectedValue = errors?.getFieldError(field)?.rejectedValue
            if (rejectedValue == null) {
                rejectedValue = parseForRejectedValue(bean, field)
            }

        } else {
            rejectedValue = parseForRejectedValue(bean, field)
        }
        if (rejectedValue != null) {
            out << formatValue(rejectedValue, field, tagSyntaxCall)
        }
    }

    private Object parseForRejectedValue(bean, field) {
        def rejectedValue = bean
        for (String fieldPart in field.split("\\.")) {
            rejectedValue = rejectedValue?."$fieldPart"
        }
        return rejectedValue
    }

    def extractErrors(attrs) {
        def model = attrs.model
        def checkList = []
        if (attrs.containsKey('bean')) {
            if (attrs.bean) {
                checkList << attrs.bean
            }
        } else if (attrs.containsKey('model')) {
            if (model) {
                checkList = model.findAll { it.value?.errors instanceof Errors }.collect { it.value }
            }
        } else {
            for (attributeName in request.attributeNames) {
                def ra = request[attributeName]
                if (ra) {
                    def mc = GroovySystem.metaClassRegistry.getMetaClass(ra.getClass())
                    if (ra instanceof Errors && !checkList.contains(ra)) {
                        checkList << ra
                    } else if (mc.hasProperty(ra, 'errors') && ra.errors instanceof Errors && !checkList.contains(ra.errors)) {
                        checkList << ra.errors
                    }
                }
            }
        }

        def resultErrorsList = []

        for (i in checkList) {
            def errors = null
            if (i instanceof Errors) {
                errors = i
            } else {
                def mc = GroovySystem.metaClassRegistry.getMetaClass(i.getClass())
                if (mc.hasProperty(i, 'errors')) {
                    errors = i.errors
                }
            }
            if (errors?.hasErrors()) {
                // if the 'field' attribute is not provided then we should output a body,
                // otherwise we should check for field-specific errors
                if (!attrs.field || errors.hasFieldErrors(attrs.field)) {
                    resultErrorsList << errors
                }
            }
        }

        resultErrorsList
    }

    /**
     * Checks if the request has errors either for a field or global errors.
     *
     * @attr bean The bean to check for errors
     * @attr field The field of the bean or model reference to check
     * @attr model The model reference to check for errors
     */
    Closure hasErrors = { attrs, body ->
        def errorsList = extractErrors(attrs)
        if (errorsList) {
            out << body()
        }
    }

    /**
     * Loops through each error of the specified bean or model. If no arguments
     * are specified it will go through all model attributes and check for errors.
     *
     * @attr bean The bean to check for errors
     * @attr field The field of the bean or model reference to check
     * @attr model The model reference to check for errors
     */
    Closure eachError = { attrs, body ->
        eachErrorInternal(attrs, body, true)
    }

    def eachErrorInternal(attrs, body, boolean outputResult = false) {
        def errorsList = extractErrors(attrs)
        eachErrorInternalForList attrs, errorsList, body, outputResult
    }

    def eachErrorInternalForList(attrs, errorsList, body, boolean outputResult = false) {
        def var = attrs.var
        def field = attrs.field

        def errorList = []
        for (errors in errorsList) {
            if (field) {
                if (errors.hasFieldErrors(field)) {
                    errorList += errors.getFieldErrors(field)
                }
            } else {
                errorList += errors.allErrors
            }
        }

        for (error in errorList) {
            def result
            if (var) {
                result = body([(var): error])
            } else {
                result = body(error)
            }
            if (outputResult) {
                out << result
            }
        }

        null
    }

    /**
     * Loops through each error and renders it using one of the supported mechanisms (defaults to "list" if unsupported).
     *
     * @emptyTag
     *
     * @attr bean The bean to check for errors
     * @attr field The field of the bean or model reference to check
     * @attr model The model reference to check for errors
     */
    Closure renderErrors = { attrs, body ->
        def renderAs = attrs.remove('as') ?: 'list'

        if (renderAs == 'list') {
            def codec = attrs.codec ?: 'HTML'
            if (codec == 'none') codec = ''

            def errorsList = extractErrors(attrs)
            if (errorsList) {
                out << "<ul>"
                out << eachErrorInternalForList(attrs, errorsList, {
                    out << "<li>${message(error: it, encodeAs: codec)}</li>"
                })
                out << "</ul>"
            }
        } else if (renderAs.equalsIgnoreCase("xml")) {
            def mkp = new MarkupBuilder(out)
            mkp.errors() {
                eachErrorInternal(attrs, {
                    error(object: it.objectName,
                            field: it.field,
                            message: message(error: it)?.toString(),
                            'rejected-value': StringEscapeUtils.escapeXml10(it.rejectedValue))
                })
            }
        }
    }

    /**
     * Resolves a message code for a given error or code from the resource bundle.
     *
     * @emptyTag
     *
     * @attr error The error to resolve the message for. Used for built-in Grails messages.
     * @attr message The object to resolve the message for. Objects must implement org.springframework.context.MessageSourceResolvable.
     * @attr code The code to resolve the message for. Used for custom application messages.
     * @attr args A list of argument values to apply to the message, when code is used.
     * @attr default The default message to output if the error or code cannot be found in messages.properties.
     * @attr encodeAs The name of a codec to apply, i.e. HTML, JavaScript, URL etc
     * @attr locale override locale to use instead of the one detected
     */
    Closure message = { attrs ->
        messageImpl(attrs)
    }

    @CompileStatic
    def messageImpl(Map attrs) {
        Locale locale = FormatTagLib.resolveLocale(attrs.locale)
        def tagSyntaxCall = (attrs instanceof GroovyPageAttributes) ? attrs.isGspTagSyntaxCall() : false

        def text
        Object error = attrs.error ?: attrs.message
        if (error) {
            if (!attrs.encodeAs && error instanceof MessageSourceResolvable) {
                MessageSourceResolvable errorResolvable = (MessageSourceResolvable) error
                if (errorResolvable.arguments) {
                    error = new DefaultMessageSourceResolvable(errorResolvable.codes, encodeArgsIfRequired(errorResolvable.arguments) as Object[], errorResolvable.defaultMessage)
                }
            }
            try {
                if (error instanceof MessageSourceResolvable) {
                    text = messageSource.getMessage(error, locale)
                } else {
                    text = messageSource.getMessage(error.toString(), null, locale)
                }
            }
            catch (NoSuchMessageException e) {
                if (error instanceof MessageSourceResolvable) {
                    text = ((MessageSourceResolvable) error).codes[0]
                } else {
                    text = error?.toString()
                }
            }
        } else if (attrs.code) {
            String code = attrs.code?.toString()
            List args = []
            if (attrs.args) {
                args = attrs.encodeAs ? attrs.args as List : encodeArgsIfRequired(attrs.args)
            }
            String defaultMessage
            if (attrs.containsKey('default')) {
                defaultMessage = attrs['default']?.toString()
            } else {
                defaultMessage = code
            }

            def message = messageSource.getMessage(code, args == null ? null : args.toArray(),
                    defaultMessage, locale)
            if (message != null) {
                text = message
            } else {
                text = defaultMessage
            }
        }
        if (text) {
            Encoder encoder = codecLookup.lookupEncoder(attrs.encodeAs?.toString() ?: 'raw')
            return encoder ? encoder.encode(text) : text
        }
        ''
    }

    @CompileStatic
    private List encodeArgsIfRequired(arguments) {
        arguments.collect { value ->
            if (value == null || value instanceof Number || value instanceof Date) {
                value
            } else {
                Encoder encoder = codecLookup.lookupEncoder('HTML')
                encoder ? encoder.encode(value) : value
            }
        }
    }

    // Maps out how Grails contraints map to Apache commons validators
    static CONSTRAINT_TYPE_MAP = [email     : 'email',
                                  creditCard: 'creditCard',
                                  matches   : 'mask',
                                  blank     : 'required',
                                  nullable  : 'required',
                                  maxSize   : 'maxLength',
                                  minSize   : 'minLength',
                                  range     : 'intRange',
                                  size      : 'intRange',
                                  length    : 'maxLength,minLength']

    /**
     * Validates a form using Apache commons validator javascript against constraints defined in a Grails
     * domain class.<br/>
     *
     * TODO: This tag is a work in progress
     *
     * @attr form REQUIRED the HTML form name
     * @attr againstClass REQUIRED the domain class name
     */
    Closure validate = { attrs, body ->
        def form = attrs.form
        if (!form) {
            throwTagError("Tag [validate] is missing required attribute [form]")
        }

        def againstClass = attrs.against ?: form.substring(0, 1).toUpperCase() + form.substring(1)
        def dc = grailsAttributes.grailsApplication.getDomainClass(againstClass)
        if (!dc) {
            throwTagError("Tag [validate] could not find a domain class to validate against for name [${againstClass}]")
        }

        def appliedConstraints = []
        dc.constrainedProperties.values().each {
            appliedConstraints += it.collect { it.appliedConstraints }
        }

        appliedConstraints = appliedConstraints.flatten()
        def fieldValidations = [:]
        appliedConstraints.each {
            def validateType = CONSTRAINT_TYPE_MAP[it.name]
            if (validateType) {
                if (fieldValidations[validateType]) {
                    fieldValidations[validateType] << it
                } else {
                    fieldValidations[validateType] = [it]
                }
            }
        }

        out << '<script type="text/javascript">\n'
        fieldValidations.each { k, v ->
            def validateType = k
            if (validateType) {
                def validateTypes = [validateType]
                if (validateType.contains(",")) {
                    validateTypes = validateType.split(",")
                }

                for (vt in validateTypes) {
                    // import required script
                    def scriptName = "org/apache/commons/validator/javascript/validate" + vt.substring(0, 1).toUpperCase() + vt.substring(1) + ".js"
                    def inStream = getClass().classLoader.getResourceAsStream(scriptName)
                    if (inStream) {
                        out << inStream.getText('UTF-8')
                    }

                    out << "function ${form}_${vt}() {"
                    for (constraint in v) {
                        out << "this.${constraint.propertyName} = new Array("
                        out << "document.forms['${form}'].elements['${constraint.propertyName}']," // the field
                        out << '"Test message"' // TODO: Resolve the actual message
                        switch (vt) {
                            case 'mask': out << ",function() { return '${constraint.regex}'; }"; break
                            case 'intRange': out << ",function() { if (arguments[0]=='min') return ${constraint.range.from}; else return ${constraint.range.to} }"; break
                            case 'floatRange': out << ",function() { if (arguments[0]=='min') return ${constraint.range.from}; else return ${constraint.range.to} }"; break
                            case 'maxLength': out << ",function() { return ${constraint.maxSize};  }"; break
                            case 'minLength': out << ",function() { return ${constraint.minSize};  }"; break
                        }
                        out << ');\n'
                    }
                    out << "}\n"
                }
            }
        }
        out << 'function validateForm(form) {\n'
        fieldValidations.each { k, v ->
            def validateType = k.substring(0, 1).toUpperCase() + k.substring(1)
            out << "if (!validate${validateType}(form)) return false;\n"
        }
        out << 'return true;\n'
        out << '}\n'
        // out << "document.forms['${attrs.form}'].onsubmit = function(e) {return validateForm(this)}\n"
        out << '</script>'
    }

    /**
     * Formats a given value for output to an HTML page by converting
     * it to a string and encoding it. If the value is a number, it is
     * formatted according to the current user's locale during the
     * conversion to a string.
     */
    def formatValue(value, String propertyPath = null, Boolean tagSyntaxCall = false) {
        def webRequest = GrailsWebRequest.lookup()
        PropertyEditorRegistry registry = webRequest.getPropertyEditorRegistry()
        PropertyEditor editor = registry.findCustomEditor(value.getClass(), propertyPath)
        if (editor) {
            editor.setValue(value)
            return !(value instanceof Number) ? editor.asText?.encodeAsHTML() : editor.asText
        }

        if (value instanceof Number) {
            def pattern = "0"
            if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
                pattern = "0.00#####"
            }

            def locale = webRequest.getLocale()
            def dcfs = locale ? new DecimalFormatSymbols(locale) : new DecimalFormatSymbols()
            def decimalFormat = new DecimalFormat(pattern, dcfs)
            value = decimalFormat.format(value)
        }

        if (value instanceof MessageSourceResolvable) {
            value = message(message: value)
        }

        return value.toString().encodeAsHTML()
    }
}

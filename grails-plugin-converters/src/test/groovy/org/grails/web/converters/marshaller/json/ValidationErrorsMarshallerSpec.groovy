package org.grails.web.converters.marshaller.json

import grails.converters.JSON

import org.grails.web.json.JSONWriter
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError

import spock.lang.Specification

class ValidationErrorsMarshallerSpec extends Specification {

    void "Test marshalObject handles org.springframework.validation.ObjectError"() {
        given:
            ObjectError objectError = new ObjectError('test', 'Error happening on test object.')

            List<ObjectError> allErrors = [objectError]

            ValidationErrorsMarshaller marshaller = new ValidationErrorsMarshaller()
            Errors errors = Mock(Errors) {
                1 * getAllErrors() >> allErrors
            }

            JSON json = new JSON()

            StringWriter stringWriter = new StringWriter()
            json.writer = new JSONWriter(stringWriter)

        when:
            marshaller.marshalObject(errors, json)

        then:
            assert stringWriter.toString() == '{"errors":[{"object":"test","message":"Error happening on test object."}]}'
    }
}

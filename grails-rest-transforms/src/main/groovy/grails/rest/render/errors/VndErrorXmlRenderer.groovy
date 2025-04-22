/*
 * Copyright 2013-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.rest.render.errors

import grails.rest.render.RenderContext
import grails.util.GrailsWebUtil
import groovy.transform.CompileStatic
import grails.web.mime.MimeType
import org.grails.web.xml.PrettyPrintXMLStreamWriter
import org.grails.web.xml.StreamingMarkupWriter
import org.grails.web.xml.XMLStreamWriter
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError
import org.springframework.http.HttpStatus

/**
 * A renderer that renders errors in in the Vnd.Error format (see https://github.com/blongden/vnd.error)
 *
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class VndErrorXmlRenderer extends AbstractVndErrorRenderer {
    public static final MimeType MIME_TYPE = new MimeType("application/vnd.error+xml", "xml")
    public static final String ERRORS_TAG = "errors"
    public static final String ERROR_TAG = "error"
    public static final String LINK_TAG = "link"

    MimeType[] mimeTypes = [MIME_TYPE, MimeType.HAL_XML, MimeType.XML, MimeType.TEXT_XML] as MimeType[]

    @Override
    void render(Errors object, RenderContext context) {
        if (object instanceof BeanPropertyBindingResult) {
            def errors = object as BeanPropertyBindingResult
            context.setContentType(GrailsWebUtil.getContentType(MIME_TYPE.name, encoding))
            context.setStatus(HttpStatus.UNPROCESSABLE_ENTITY)
            Locale locale = context.locale
            final target = errors.target
            final language = locale.language

            final streamingWriter = new StreamingMarkupWriter(context.writer, encoding)
            XMLStreamWriter w = prettyPrint ? new PrettyPrintXMLStreamWriter(streamingWriter) : new XMLStreamWriter(streamingWriter)
            w.startDocument(encoding, "1.0")
            w.startNode(ERRORS_TAG)
                .attribute('xml:lang', language)
            for (ObjectError oe in errors.allErrors) {
                def logref = resolveLogRef(target, oe)
                w.startNode(ERROR_TAG)
                    .attribute(LOGREF_ATTRIBUTE, logref)
                    .startNode(MESSAGE_ATTRIBUTE)
                        .characters(messageSource.getMessage(oe, locale))
                    .end()
                    .startNode(LINK_TAG)
                        .attribute("rel", "resource")
                        .attribute("href", linkGenerator.link(resource: target, method: "GET", absolute: true))
                    .end()
                .end()
            }
            w.end()
        }
    }
}

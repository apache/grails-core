/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.rest.render.errors

import grails.rest.render.RenderContext
import grails.util.GrailsWebUtil
import groovy.json.JsonOutput
import groovy.json.StreamingJsonBuilder
import groovy.transform.CompileStatic
import grails.web.mime.MimeType
import org.springframework.http.HttpMethod
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError
import org.springframework.http.HttpStatus

/**
 * A JSON renderer that renders errors in in the Vnd.Error format (see https://github.com/blongden/vnd.error)
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class VndErrorJsonRenderer extends AbstractVndErrorRenderer {
    public static final MimeType MIME_TYPE = new MimeType("application/vnd.error+json", "json")
    public static final String LINKS_ATTRIBUTE = "_links"
    public static final String FOUR_SPACES = '    '

    MimeType[] mimeTypes = [MIME_TYPE, MimeType.HAL_JSON, MimeType.JSON, MimeType.TEXT_JSON] as MimeType[]

    @Override
    void render(Errors object, RenderContext context) {
        if (messageSource == null) throw new IllegalStateException("messageSource property null")
        if (object instanceof BeanPropertyBindingResult) {

            def errors = object as BeanPropertyBindingResult
            context.setContentType(GrailsWebUtil.getContentType(MIME_TYPE.name, encoding))
            context.setStatus(HttpStatus.UNPROCESSABLE_ENTITY)
            Locale locale = context.locale
            final target = errors.target

            def responseWriter = context.writer
            Writer targetWriter = prettyPrint ? new StringWriter() : responseWriter
            StreamingJsonBuilder writer = new StreamingJsonBuilder(targetWriter)

            writer.call(object.allErrors) { ObjectError oe ->
                final msg = messageSource.getMessage(oe, locale)
                final String logref = resolveLogRef(target, oe)
                final String path = linkGenerator.link(resource: target, method: HttpMethod.GET, absolute: absoluteLinks)

                delegate.call(LOGREF_ATTRIBUTE, logref)
                delegate.call(MESSAGE_ATTRIBUTE, msg)
                delegate.call(PATH_ATTRIBUTE, path)
                delegate.call(LINKS_ATTRIBUTE) {
                    delegate.call(RESOURCE_ATTRIBUTE) {
                        delegate.call(HREF_ATTRIBUTE, path)
                    }
                }

            }

            targetWriter.flush()
            if(prettyPrint) {
                responseWriter.write(JsonOutput.prettyPrint(targetWriter.toString()))
            }
        }
    }
}

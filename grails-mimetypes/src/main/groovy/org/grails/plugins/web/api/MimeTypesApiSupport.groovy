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
package org.grails.plugins.web.api

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import jakarta.servlet.ServletRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import grails.web.mime.MimeType
import org.grails.plugins.web.mime.FormatInterceptor
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Support class for dealing with calls to withFormat.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class MimeTypesApiSupport {

    def <T> T withFormat(HttpServletRequest request, Closure<T> callable) {
        return (T)withFormatInternal(request, getDefinedFormats(callable))
    }

    def <T> T withFormat(HttpServletResponse response, Closure<T> callable) {
        return (T)withFormatInternal(response, getDefinedFormats(callable))
    }

    protected Object withFormatInternal(formatProvider, LinkedHashMap<String, Object> formats) {
        def result = null
        String format = lookupFormat(formatProvider)
        if (formats) {
            if (format == 'all') {
                result = resolveAllFormat(formatProvider, formats)
            }
            else {
                // if the format has been specified then use that
                if (formats.containsKey(format)) {
                    result = getResponseForFormat(formats[format], format, formatProvider)
                }
                // otherwise look for the best match
                else {
                    boolean matchFound = false
                    for (MimeType mime in lookupMimeTypes(formatProvider)) {
                        if (formats.containsKey(mime.extension)) {
                            matchFound = true
                            result = getResponseForFormat(formats[mime.extension], mime.extension, formatProvider)
                            break
                        }
                        else {
                            if (mime.extension == 'all') {
                                matchFound = true
                                result = resolveAllFormat(formatProvider, formats)
                                break
                            }
                        }
                    }
                    if (!matchFound && formats.containsKey('*')) {
                        result = getResponseForFormat(formats['*'], format, formatProvider)
                    }
                }
            }
        }
        return result
    }

    /**
     * implementation for resolving "all" format
     *
     * @param formatProvider
     * @param formats
     * @return
     */
    protected Object resolveAllFormat(formatProvider, LinkedHashMap<String, Object> formats) {
        def formatKey
        def format
        if(formats.containsKey('*')) {
            formatKey = '*'
            format = 'all'
        } else {
            // choose first key
            formatKey = formats.keySet().iterator().next()
            format = formatKey
        }
        getResponseForFormat(formats[formatKey], format, formatProvider)
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected MimeType[] lookupMimeTypes(formatProvider) {
        formatProvider.mimeTypes
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected String lookupFormat(formatProvider) {
        formatProvider.format
    }

    LinkedHashMap<String, Object> getDefinedFormats(Closure callable) {
        LinkedHashMap<String, Object> formats = null
        def original = callable.delegate
        try {
            final interceptor = new FormatInterceptor()
            callable.delegate = interceptor
            callable.resolveStrategy = Closure.DELEGATE_ONLY
            callable.call()
            formats = interceptor.formatOptions
        }
        finally {
            callable.delegate = original
            callable.resolveStrategy = Closure.OWNER_FIRST
        }
        return formats
    }

    private Object getResponseForFormat(formatResponse, format, formatProvider) {
        if (formatProvider instanceof ServletRequest) {
            formatProvider.setAttribute(GrailsApplicationAttributes.CONTENT_FORMAT, format)
        }
        else {
            GrailsWebRequest.lookup().currentRequest.setAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT, format)
        }

        if (formatResponse instanceof Closure) {
            return formatResponse?.call()
        }
        else {
            return formatResponse
        }

    }
}

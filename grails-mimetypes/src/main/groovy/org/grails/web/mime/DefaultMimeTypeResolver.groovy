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
package org.grails.web.mime

import groovy.transform.CompileStatic

import grails.web.mime.MimeType
import grails.web.mime.MimeTypeResolver
import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Resolves the {@link grails.web.mime.MimeType} instance for a request
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class DefaultMimeTypeResolver implements MimeTypeResolver {

    /**
     * Resolve the {@link grails.web.mime.MimeType} to be used for the response, typically established from the ACCEPT header
     *
     * @since 2.3
     * @return the {@link grails.web.mime.MimeType}
     */
    @Override
    MimeType resolveResponseMimeType(GrailsWebRequest webRequest= GrailsWebRequest.lookup()) {
        if (webRequest != null) {
            return HttpServletResponseExtension.getMimeType(webRequest.response)
        }
        return null
    }

    /**
     * Resolve the {@link MimeType} to be used for the request, typically established from the CONTENT_TYPE header
     *
     * @since 2.3
     * @return the {@link MimeType}
     */
    @Override
    MimeType resolveRequestMimeType(GrailsWebRequest webRequest = GrailsWebRequest.lookup()) {
        if (webRequest != null) {
            final allMimeTypes = HttpServletRequestExtension.getMimeTypes(webRequest.request)
            if(allMimeTypes) {
                return allMimeTypes[0]
            }
        }
        return null
    }
}

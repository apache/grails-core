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
package grails.web.mime

import groovy.transform.CompileStatic
import jakarta.servlet.http.HttpServletRequest

/**
 * Utility methods for MimeType handling
 *
 * @author Graeme Rocher
 * @since 2.4
 */
@CompileStatic
class MimeTypeUtils {

    static MimeType resolveMimeType(Object source, MimeTypeResolver mimeTypeResolver) {
        final MimeType mimeType
        if (mimeTypeResolver) {
            def resolvedMimeType = mimeTypeResolver.resolveRequestMimeType()
            mimeType = resolvedMimeType ? resolvedMimeType : MimeType.ALL
        } else if (source instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) source
            String contentType = req.contentType
            if (contentType != null) {
                mimeType = new MimeType(contentType)
            } else {
                mimeType = MimeType.ALL
            }
        } else {
            mimeType = MimeType.ALL
        }
        return mimeType
    }
}

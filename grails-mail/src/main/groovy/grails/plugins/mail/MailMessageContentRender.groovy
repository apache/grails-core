/*
 * Copyright 2010 the original author or authors.
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

package grails.plugins.mail

import groovy.transform.CompileStatic

/**
 * Represents the result of rendering a message content view.
 */
@CompileStatic
class MailMessageContentRender {

    private static String[] HTML_CONTENT_TYPES = ['text/html', 'text/xhtml']

    final Writer out
    final String contentType

    MailMessageContentRender(Writer out, String contentType) {
        this.out = out
        this.contentType = contentType
    }

    boolean isHtml() {
        contentType in HTML_CONTENT_TYPES
    }
}

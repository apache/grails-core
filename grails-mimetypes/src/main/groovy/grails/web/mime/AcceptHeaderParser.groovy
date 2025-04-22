/*
 * Copyright 2004-2005 the original author or authors.
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
package grails.web.mime
/**
 * <p>Defines an interface for classes that parse the HTTP Accept header into a set or MimeType definitions ordered by
 * priority.
 *
 * <p>The ordering is based on the order they appear in the Accept header as well as the 'q' (for quality) parameter. A mime type definition of 'text/xml;q=0.6' will
 *  have a higher priorty than 'text/html' due to the q parameter
 *
 * @author Graeme Rocher
 * @since 1.0
 */
interface AcceptHeaderParser {

    /**
     * Parses an Accept header into an ordered array of MimeType definitions
     */
    MimeType[] parse(String header)

    /**
     * Parses an Accept header into an ordered array of MimeType definitions
     */
    MimeType[] parse(String header, MimeType fallbackMimeType)
}

/*
 * Copyright 2011 SpringSource
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
package grails.web.mime;

import grails.web.mime.MimeType;

import java.util.List;

/**
 * @author Graeme Rocher
 * @since 2.0
 */
public interface MimeUtility {

    /**
     * Gets the known configured MimeType instances
     *
     * @return An array of MimeType instances
     */
    List<MimeType> getKnownMimeTypes();

    /**
     * Obtains a MimeType for the given extension
     *
     * @param extension The extension
     * @return The MimeType instance or null if not known
     */
    MimeType getMimeTypeForExtension(String extension);

    /**
     * Obtains a MimeType for the given extension
     *
     * @param uri The URI to use
     * @return The MimeType instance or null if not known
     */
    MimeType getMimeTypeForURI(String uri);
}

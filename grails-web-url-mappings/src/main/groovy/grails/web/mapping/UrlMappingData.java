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
package grails.web.mapping;

/**
 * <p>Holds information about a parsed URL such as the tokens that make up the URL, The URLs (plural)
 * that the UrLMapping logically maps to and so forth</p>
 *
 * @author Graeme Rocher
 * @since 0.5
 */
public interface UrlMappingData {

    /**
     * <p>Retrieves the tokens that make up a URL. For example the tokens for the URL /blog/2007/* would
     * be "blog", "2007" and "*"
     *
      * @return The tokens as a string array
     */
    String[] getTokens();

    /**
     * <p>Obtains the logical URLs for this URL</p>
     *
     * @return The logical URLs as a string array
     */
    String[] getLogicalUrls();

    /**
     * Retrieves the URL pattern for this UrlMappingData instance
     *
     * @return The URL pattern
     */
    String getUrlPattern();

    /**
     * Returns whether the given token in the URL is optional. The index takes into account matching groups
     * so for example the URL /blog/(*)/(*) has two entries for the two (*) matching groups with the index 0
     * relating to the the first entry
     *
     * @param index The index of the matching token
     * @return  True if it is optional
     */
    boolean isOptional(int index);

    /**
     * Creates a new UrlMappingData instance relative to this one for the given path
     * @param path The path
     * @return The new instance
     */
    UrlMappingData createRelative(String path);

    /**
     * @return Whether the pattern has an optional extension
     */
    boolean hasOptionalExtension();
}

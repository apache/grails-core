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
 * <p>Parses a Grails URL mapping into a UrlMappingData object that holds various information about the mapping</p>
 *
 * <p>A Grails URL pattern is not a regex, but is an extension to the form defined by Apache Ant and used by
 * Spring AntPathMatcher. Unlike regular Ant paths Grails URL patterns allow for capturing groups in the form:</p>
 *
 * <code>/blog/(*)&#47;**</code>
 *
 * <p>The parenthesis define a capturing group. This implementation transforms regular Ant paths into regular expressions
 * that are able to use capturing groups</p>
 *
 * @author Graeme Rocher
 * @since 0.5
 */
public interface UrlMappingParser {

    /**
     * Parses the given URI pattern into a UrlMappingData instance
     *
     * @param url The URL pattern to parse
     * @return The UrlMappingData instance
     */
    UrlMappingData parse(String url);
}

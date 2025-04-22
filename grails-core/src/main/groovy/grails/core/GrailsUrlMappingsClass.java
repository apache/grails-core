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
package grails.core;

import groovy.lang.Closure;

import java.util.List;

/**
 * Loads the UrlMappings.
 */
public interface GrailsUrlMappingsClass extends GrailsClass {

    /**
     * Returns the mappings closure which is called to evaluate the url mappings.
     *
     * @return A Closure instance
     */
    @SuppressWarnings("rawtypes")
    Closure getMappingsClosure();

    /**
     * Returns a List of URI patterns to exclude.
     * @return  the patterns (Strings)
     */
    @SuppressWarnings("rawtypes")
    List getExcludePatterns();
}

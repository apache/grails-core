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
package org.grails.core;

import grails.core.GrailsUrlMappingsClass;
import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.List;

public class DefaultGrailsUrlMappingsClass extends AbstractGrailsClass implements GrailsUrlMappingsClass {

    public static final String URL_MAPPINGS = "UrlMappings";

    private static final String MAPPINGS_CLOSURE = "mappings";
    private static final String EXCLUDE_PATTERNS = "excludes";

    public DefaultGrailsUrlMappingsClass(Class<?> clazz) {
        super(clazz, URL_MAPPINGS);
    }

    public Closure<?> getMappingsClosure() {
        Closure<?> result = getStaticPropertyValue(MAPPINGS_CLOSURE, Closure.class);
        if (result == null) {
            throw new RuntimeException(MAPPINGS_CLOSURE + " closure does not exists for class " +  getClazz().getName());
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    public List getExcludePatterns() {
        return getStaticPropertyValue(EXCLUDE_PATTERNS, ArrayList.class);
    }
}

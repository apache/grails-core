/*
 * Copyright 2013 the original author or authors.
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

import groovy.lang.Closure;

import java.util.Collection;

/**
 * Interface that allows access to all defined URL mappings and registration of new mappings at runtime
 *
 *
 * @author Graeme Rocher
 * @since 2.3
 */
public interface UrlMappings extends UrlMappingsHolder {

    /**
     * Adds URL mappings to the current definition for the given closure
     *
     * @param mappings The mappings
     * @return Only the added mappings. To obtain all mappings use {@link UrlMappingsHolder#getUrlMappings()}
     */
    Collection<UrlMapping> addMappings(Closure mappings);
}

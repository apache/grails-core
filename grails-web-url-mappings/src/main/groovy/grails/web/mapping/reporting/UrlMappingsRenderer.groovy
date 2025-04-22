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
package grails.web.mapping.reporting

import grails.web.mapping.UrlMapping

/**
 * An interface for classes to implement that renders URL mappings to some output format
 *
 * @author Graeme Rocher
 * @since 2.3
 */
public interface UrlMappingsRenderer {

    /**
     * Render the URL mappings to whatever output destination is defined by the implementation
     *
     * @param urlMappings The URL mappings
     */
    void render(List<UrlMapping> urlMappings)
}
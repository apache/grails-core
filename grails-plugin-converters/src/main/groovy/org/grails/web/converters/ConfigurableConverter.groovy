/*
 * Copyright 2012 the original author or authors.
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

package org.grails.web.converters

/**
 * Interface for converters that can be configured at runtime
 *
 * @author Graeme Rocher
 * @since 2.3
 */
public interface ConfigurableConverter<W> extends Converter<W>{
    /**
     * Sets the content type of the converter
     * @param contentType The content type
     */
    void setContentType(String contentType)

    /**
     * Sets the encoding of the converter
     * @param encoding The encoding
     */
    void setEncoding(String encoding)

    /**
     * Set to include properties for the given type
     * @param type The type
     * @param properties The properties
     */
    void setIncludes(Class type, List<String> properties)

    /**
     * Set to exclude properties for the given type
     * @param type The type
     * @param properties The properties
     */
    void setExcludes(Class type, List<String> properties)

    /**
     * Gets the excludes for the given type
     * @param type The type
     * @return The excludes
     */
    List<String> getExcludes(Class type)

    /**
     * Gets the includes for the given type
     * @param type The type
     * @return The includes
     */
    List<String> getIncludes(Class type)
}
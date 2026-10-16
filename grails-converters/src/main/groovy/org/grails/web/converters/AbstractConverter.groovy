/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.web.converters

import groovy.transform.CompileStatic

import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl

import org.grails.buffer.FastStringWriter

/**
 * Abstract base implementation of the Converter interface that provides a default toString() implementation.
 *
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 */
@CompileStatic
abstract class AbstractConverter<W> implements ConfigurableConverter<W>, Writable {

    protected String contentType
    protected String encoding = 'UTF-8'
    protected Map<Class, List<String>> includes = new LinkedHashMap<>()
    protected Map<Class, List<String>> excludes = new LinkedHashMap<>()

    abstract void setTarget(Object target)

    /**
     * Sets the content type of the converter
     *
     * @param contentType The content type
     */
    @Override
    void setContentType(String contentType) {
        this.contentType = contentType
    }

    /**
     * Sets the encoding of the converter
     *
     * @param encoding The encoding
     */
    @Override
    void setEncoding(String encoding) {
        this.encoding = encoding
    }

    /**
     * Set to include properties for the given type
     *
     * @param type The type
     * @param properties The properties
     */
    @Override
    void setIncludes(Class type, List<String> properties) {
        includes.put(type, properties)
    }

    /**
     * Set to exclude properties for the given type
     *
     * @param type The type
     * @param properties The properties
     */
    @Override
    void setExcludes(Class type, List<String> properties) {
        excludes.put(type, properties)
    }

    /**
     * Gets the excludes for the given type
     *
     * @param type The type
     * @return The excludes
     */
    @Override
    List<String> getExcludes(Class type) {
        return excludes.get(type)
    }

    /**
     * Gets the includes for the given type
     *
     * @param type The type
     * @return The includes
     */
    @Override
    List<String> getIncludes(Class type) {
        return includes.get(type)
    }

    @Override
    Writer writeTo(Writer out) throws IOException {
        render(out)
        return out
    }

    @Override
    String toString() {
        FastStringWriter writer = new FastStringWriter()
        try {
            render(writer)
        }
        catch (Exception e) {
            throw new RuntimeException(e)
        }
        return writer.toString()
    }

    protected BeanWrapper createBeanWrapper(Object o) {
        return new BeanWrapperImpl(o)
    }

}

/*
 * Copyright 2006-2007 Graeme Rocher
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
package org.grails.web.converters;

import groovy.lang.Writable;
import org.grails.buffer.FastStringWriter;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base implementation of the Converter interface that provides a default toString() implementation.
 *
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 */
public abstract class AbstractConverter<W> implements ConfigurableConverter<W>, Writable {

    protected String contentType;
    protected String encoding = "UTF-8";
    protected Map<Class, List<String>> includes = new LinkedHashMap<Class, List<String>>();
    protected Map<Class, List<String>> excludes = new LinkedHashMap<Class, List<String>>();

    public abstract void setTarget(Object target);

    /**
     * Sets the content type of the converter
     *
     * @param contentType The content type
     */
    @Override
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Sets the encoding of the converter
     *
     * @param encoding The encoding
     */
    @Override
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Set to include properties for the given type
     *
     * @param type The type
     * @param properties The properties
     */
    @Override
    public void setIncludes(Class type, List<String> properties) {
        includes.put(type, properties);
    }

    /**
     * Set to exclude properties for the given type
     *
     * @param type The type
     * @param properties The properties
     */
    @Override
    public void setExcludes(Class type, List<String> properties) {
        excludes.put(type, properties);
    }

    /**
     * Gets the excludes for the given type
     *
     * @param type The type
     * @return The excludes
     */
    @Override
    public List<String> getExcludes(Class type) {
        return excludes.get(type);
    }

    /**
     * Gets the includes for the given type
     *
     * @param type The type
     * @return The includes
     */
    @Override
    public List<String> getIncludes(Class type) {
        return includes.get(type);
    }

    @Override
    public Writer writeTo(Writer out) throws IOException {
        render(out);
        return out;
    }

    @Override
    public String toString() {
        FastStringWriter writer = new FastStringWriter();
        try {
            render(writer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return writer.toString();
    }

    protected BeanWrapper createBeanWrapper(Object o) {
        return new BeanWrapperImpl(o);
    }
}

/*
 * Copyright 2006-2024 the original author or authors.
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
package org.grails.web.converters;

import groovy.lang.Closure;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * Defines an Object that can convert an instance and render it to the
 * response or a supplied writer.
 *
 * @author Siegfried Puchbauer
 */
public interface Converter<W> {

    String DEFAULT_REQUEST_ENCODING = "UTF-8";

    /**
     * Marshalls the target and writes it to a java.io.Writer
     *
     * @param out The Writer to write to
     * @throws ConverterException
     */
    public void render(Writer out) throws ConverterException;

    /**
     * Marshalls the target and writes it a HttpServletResponse
     * The response will be comitted after this operation
     *
     * @param response The response to write to
     * @throws ConverterException
     */
    public void render(HttpServletResponse response) throws ConverterException;

    W getWriter() throws ConverterException;

    void convertAnother(Object o) throws ConverterException;

    void build(@SuppressWarnings("rawtypes") Closure c) throws ConverterException;

    @SuppressWarnings("rawtypes")
    ObjectMarshaller<? extends Converter> lookupObjectMarshaller(Object target);

    enum CircularReferenceBehaviour {
        DEFAULT,
        EXCEPTION,
        INSERT_NULL,
        IGNORE,
        PATH;

        public static List<String> allowedValues() {
            List<String> v = new ArrayList<String>();
            for (CircularReferenceBehaviour crb : values()) {
                v.add(crb.name());
            }
            return v;
        }
    }
}

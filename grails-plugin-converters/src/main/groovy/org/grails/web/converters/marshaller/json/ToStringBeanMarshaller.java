/*
 * Copyright 2004-2008 the original author or authors.
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
package org.grails.web.converters.marshaller.json;

import grails.converters.JSON;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class ToStringBeanMarshaller implements ObjectMarshaller<JSON> {

    private final Set<Class<?>> classes;

    public ToStringBeanMarshaller(Set<Class<?>> classes) {
        this.classes = classes;
    }

    @SuppressWarnings("unchecked")
    public ToStringBeanMarshaller() {
        classes = Collections.unmodifiableSet(new HashSet<Class<?>>(Arrays.asList(
                Currency.class, TimeZone.class, Locale.class, URL.class)));
    }

    public boolean supports(Object object) {
        return classes.contains(object.getClass());
    }

    public void marshalObject(Object object, JSON converter) throws ConverterException {
        converter.convertAnother(object.toString());
    }
}

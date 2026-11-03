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
@SuppressWarnings('UnnecessarySemicolon')
package org.grails.web.converters.marshaller.json;

import groovy.transform.CompileStatic;

import grails.converters.JSON;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 * @deprecated with {@code grails.converters.json.legacy} set to {@code false}, as a later release does by default,
 *     the converter writes URL, Locale and other values with the application's {@code JsonMapper}; this marshaller is registered while
 *     it is {@code true}, the default in Grails 9, to render JSON as Grails 8 did.
 */
// TODO(groovyism-cleanup): semicolons kept to preserve rename-similarity against the original .java (see matrei's review on #16576). Removed by the stacked groovyism-cleanup PR.
@SuppressWarnings(['UnnecessarySemicolon'])
@Deprecated(since = '9.0')
@CompileStatic
class ToStringBeanMarshaller implements ObjectMarshaller<JSON> {

    private final Set<Class<?>> classes;

    ToStringBeanMarshaller(Set<Class<?>> classes) {
        this.classes = classes;
    }

    @SuppressWarnings('unchecked')
    ToStringBeanMarshaller() {
        classes = Collections.unmodifiableSet(new HashSet<Class<?>>(Arrays.asList(
                Currency, TimeZone, Locale, URL)));
    }

    boolean supports(Object object) {
        return classes.contains(object.getClass());
    }

    void marshalObject(Object object, JSON converter) throws ConverterException {
        converter.convertAnother(object.toString());
    }
}

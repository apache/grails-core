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
package org.grails.web.converters.configuration;

import grails.core.support.proxy.ProxyHandler;
import org.grails.web.converters.Converter;
import org.grails.web.converters.marshaller.ObjectMarshaller;

import java.util.List;

/**
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 * @since 1.1
 */
@SuppressWarnings("rawtypes")
public interface ConverterConfiguration<C extends Converter> {

    /**
     * Lookup the ProxyHandler used to deal with proxies instances.
     *
     * @return The proxy handler
     */
    ProxyHandler getProxyHandler();

    /**
     * Lookup the ObjectMarshaller with the highest priority that support to marshall the given object
     *
     * @param o the object which is about to be converted
     * @return the ObjectMarshaller instance
     */
    ObjectMarshaller<C> getMarshaller(Object o);

    /**
     * Lookup the configured default Character encoding for the Converter
     *
     * @return the Charset name
     */
    String getEncoding();

    /**
     * Lookup the configured CircularReferenceBehaviour (how the converter should behave when a circular reference is detected)
     *
     * @return an instance of CircularReferenceBehaviour
     * @see org.grails.web.converters.Converter.CircularReferenceBehaviour
     */
    Converter.CircularReferenceBehaviour getCircularReferenceBehaviour();

    /**
     * Lookup method whether the converter should default to pretty printed output
     *
     * @return a boolean
     */
    boolean isPrettyPrint();

    /**
     * Retrieve the ordered list of ObjectMarshallers
     *
     * @return the List of ObjectMarshallers ordered by priority
     */
    List<ObjectMarshaller<C>> getOrderedObjectMarshallers();

    /**
     * Enables caching object marshaller selection by class.
     *
     * @return a boolean
     */
    boolean isCacheObjectMarshallerByClass();
}

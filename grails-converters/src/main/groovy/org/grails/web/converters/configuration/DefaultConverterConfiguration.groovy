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
package org.grails.web.converters.configuration

import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.CompileStatic

import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import org.grails.web.converters.Converter
import org.grails.web.converters.marshaller.ClosureObjectMarshaller
import org.grails.web.converters.marshaller.ObjectMarshaller

/**
 * Mutable Converter Configuration with an priority sorted set of ObjectMarshallers
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
@SuppressWarnings('rawtypes')
@CompileStatic
class DefaultConverterConfiguration<C extends Converter> implements ConverterConfiguration<C> {

    public static final int DEFAULT_PRIORITY = 0

    private static final AtomicInteger MARSHALLER_SEQUENCE = new AtomicInteger(0)

    private ConverterConfiguration<C> delegate
    private String encoding
    private boolean prettyPrint = false
    private final SortedSet<Entry> objectMarshallers = new TreeSet<>()
    private Converter.CircularReferenceBehaviour circularReferenceBehaviour
    private ProxyHandler proxyHandler
    private boolean cacheObjectMarshallerByClass = true

    String getEncoding() {
        return encoding != null ? encoding : (delegate != null ? delegate.getEncoding() : null)
    }

    void setEncoding(String encoding) {
        this.encoding = encoding
    }

    Converter.CircularReferenceBehaviour getCircularReferenceBehaviour() {
        return circularReferenceBehaviour != null ? circularReferenceBehaviour : (delegate != null ? delegate.getCircularReferenceBehaviour() : null)
    }

    boolean isPrettyPrint() {
        return prettyPrint
    }

    void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint
    }

    List<ObjectMarshaller<C>> getOrderedObjectMarshallers() {
        List<ObjectMarshaller<C>> list = new ArrayList<>()
        for (Entry entry : objectMarshallers) {
            list.add(entry.marshaller)
        }
        if (delegate != null) {
            for (ObjectMarshaller<C> om : delegate.getOrderedObjectMarshallers()) {
                list.add(om)
            }
        }
        return list
    }

    void setCircularReferenceBehaviour(Converter.CircularReferenceBehaviour circularReferenceBehaviour) {
        this.circularReferenceBehaviour = circularReferenceBehaviour
    }

    DefaultConverterConfiguration() {
        proxyHandler = new DefaultProxyHandler()
    }

    DefaultConverterConfiguration(ConverterConfiguration<C> delegate) {
        this()
        this.delegate = delegate
        prettyPrint = delegate.isPrettyPrint()
        circularReferenceBehaviour = delegate.getCircularReferenceBehaviour()
        encoding = delegate.getEncoding()
    }

    DefaultConverterConfiguration(ProxyHandler proxyHandler) {
        this.proxyHandler = proxyHandler
    }

    DefaultConverterConfiguration(ConverterConfiguration<C> delegate, ProxyHandler proxyHandler) {
        this(proxyHandler)
        this.delegate = delegate
        prettyPrint = delegate.isPrettyPrint()
        circularReferenceBehaviour = delegate.getCircularReferenceBehaviour()
        encoding = delegate.getEncoding()
    }

    DefaultConverterConfiguration(List<ObjectMarshaller<C>> oms) {
        this()
        int initPriority = -1
        for (ObjectMarshaller<C> om : oms) {
            registerObjectMarshaller(om, initPriority--)
        }
    }

    DefaultConverterConfiguration(List<ObjectMarshaller<C>> oms, ProxyHandler proxyHandler) {
        this(oms)
        this.proxyHandler = proxyHandler
    }

    void registerObjectMarshaller(ObjectMarshaller<C> marshaller) {
        registerObjectMarshaller(marshaller, DEFAULT_PRIORITY)
    }

    void registerObjectMarshaller(ObjectMarshaller<C> marshaller, int priority) {
        objectMarshallers.add(new Entry(marshaller, priority))
    }

    void registerObjectMarshaller(Class<?> c, int priority, Closure callable) {
        registerObjectMarshaller(new ClosureObjectMarshaller<>(c, callable), priority)
    }

    void registerObjectMarshaller(Class<?> c, Closure callable) {
        registerObjectMarshaller(new ClosureObjectMarshaller<>(c, callable))
    }

    ObjectMarshaller<C> getMarshaller(Object o) {
        for (Entry entry : objectMarshallers) {
            if (entry.marshaller.supports(o)) {
                return entry.marshaller
            }
        }
        return delegate != null ? delegate.getMarshaller(o) : null
    }

    @SuppressWarnings('rawtypes')
    class Entry implements Comparable {

        protected final ObjectMarshaller<C> marshaller
        private final int priority
        private final int seq

        private Entry(ObjectMarshaller<C> marshaller, int priority) {
            this.marshaller = marshaller
            this.priority = priority
            seq = MARSHALLER_SEQUENCE.incrementAndGet()
        }

        int compareTo(Object o) {
            Entry entry = (Entry) o
            return priority == entry.priority ? entry.seq - seq : entry.priority - priority
        }

    }

    ProxyHandler getProxyHandler() {
        return proxyHandler
    }

    boolean isCacheObjectMarshallerByClass() {
        return cacheObjectMarshallerByClass
    }

    void setCacheObjectMarshallerByClass(boolean cacheObjectMarshallerByClass) {
        this.cacheObjectMarshallerByClass = cacheObjectMarshallerByClass
    }

}

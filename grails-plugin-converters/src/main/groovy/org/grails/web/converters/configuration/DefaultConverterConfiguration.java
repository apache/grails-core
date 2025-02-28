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
package org.grails.web.converters.configuration;

import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import grails.core.support.proxy.DefaultProxyHandler;
import grails.core.support.proxy.ProxyHandler;

import org.grails.web.converters.Converter;
import org.grails.web.converters.marshaller.ClosureObjectMarshaller;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * Mutable Converter Configuration with an priority sorted set of ObjectMarshallers
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
@SuppressWarnings("rawtypes")
public class DefaultConverterConfiguration<C extends Converter> implements ConverterConfiguration<C> {

    public static final int DEFAULT_PRIORITY = 0;

    private static final AtomicInteger MARSHALLER_SEQUENCE = new AtomicInteger(0);

    private ConverterConfiguration<C> delegate;
    private String encoding;
    private boolean prettyPrint = false;
    private final SortedSet<Entry> objectMarshallers = new TreeSet<Entry>();
    private Converter.CircularReferenceBehaviour circularReferenceBehaviour;
    private ProxyHandler proxyHandler;
    private boolean cacheObjectMarshallerByClass = true;

    public String getEncoding() {
        return encoding != null ? encoding : (delegate != null ? delegate.getEncoding() : null);
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Converter.CircularReferenceBehaviour getCircularReferenceBehaviour() {
        return circularReferenceBehaviour != null ? circularReferenceBehaviour : (delegate != null ? delegate.getCircularReferenceBehaviour(): null);
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public List<ObjectMarshaller<C>> getOrderedObjectMarshallers() {
        List<ObjectMarshaller<C>> list = new ArrayList<ObjectMarshaller<C>>();
        for (Entry entry : objectMarshallers) {
            list.add(entry.marshaller);
        }
        if (delegate != null) {
            for (ObjectMarshaller<C> om : delegate.getOrderedObjectMarshallers()) {
                list.add(om);
            }
        }
        return list;
    }

    public void setCircularReferenceBehaviour(Converter.CircularReferenceBehaviour circularReferenceBehaviour) {
        this.circularReferenceBehaviour = circularReferenceBehaviour;
    }

    public DefaultConverterConfiguration() {
        proxyHandler = new DefaultProxyHandler();
    }

    public DefaultConverterConfiguration(ConverterConfiguration<C> delegate) {
        this();
        this.delegate = delegate;
        prettyPrint = delegate.isPrettyPrint();
        circularReferenceBehaviour = delegate.getCircularReferenceBehaviour();
        encoding = delegate.getEncoding();
    }

    public DefaultConverterConfiguration(ProxyHandler proxyHandler) {
        this.proxyHandler = proxyHandler;
    }

    public DefaultConverterConfiguration(ConverterConfiguration<C> delegate, ProxyHandler proxyHandler) {
        this(proxyHandler);
        this.delegate = delegate;
        prettyPrint = delegate.isPrettyPrint();
        circularReferenceBehaviour = delegate.getCircularReferenceBehaviour();
        encoding = delegate.getEncoding();
    }

    public DefaultConverterConfiguration(List<ObjectMarshaller<C>> oms) {
        this();
        int initPriority = -1;
        for (ObjectMarshaller<C> om : oms) {
            registerObjectMarshaller(om, initPriority--);
        }
    }

    public DefaultConverterConfiguration(List<ObjectMarshaller<C>> oms, ProxyHandler proxyHandler) {
        this(oms);
        this.proxyHandler = proxyHandler;
    }

    public void registerObjectMarshaller(ObjectMarshaller<C> marshaller) {
        registerObjectMarshaller(marshaller, DEFAULT_PRIORITY);
    }

    public void registerObjectMarshaller(ObjectMarshaller<C> marshaller, int priority) {
        objectMarshallers.add(new Entry(marshaller, priority));
    }

    public void registerObjectMarshaller(Class<?> c, int priority, Closure callable) {
        registerObjectMarshaller(new ClosureObjectMarshaller<C>(c, callable), priority);
    }

    public void registerObjectMarshaller(Class<?> c, Closure callable) {
        registerObjectMarshaller(new ClosureObjectMarshaller<C>(c, callable));
    }

    public ObjectMarshaller<C> getMarshaller(Object o) {
        for (Entry entry : objectMarshallers) {
            if (entry.marshaller.supports(o)) {
                return entry.marshaller;
            }
        }
        return delegate != null ? delegate.getMarshaller(o) : null;
    }

    public class Entry implements Comparable<Entry> {
        protected final ObjectMarshaller<C> marshaller;
        private final int priority;
        private final int seq;

        private Entry(ObjectMarshaller<C> marshaller, int priority) {
            this.marshaller = marshaller;
            this.priority = priority;
            seq = MARSHALLER_SEQUENCE.incrementAndGet();
        }

        public int compareTo(Entry entry) {
            return priority == entry.priority ? entry.seq - seq : entry.priority - priority;
        }
    }

    public ProxyHandler getProxyHandler() {
        return proxyHandler;
    }

    public boolean isCacheObjectMarshallerByClass() {
        return cacheObjectMarshallerByClass;
    }

    public void setCacheObjectMarshallerByClass(boolean cacheObjectMarshallerByClass) {
        this.cacheObjectMarshallerByClass = cacheObjectMarshallerByClass;
    }
}

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

import java.util.Collections;
import java.util.List;

import grails.core.support.proxy.DefaultProxyHandler;
import grails.core.support.proxy.ProxyHandler;

import org.grails.web.converters.Converter;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * Immutable Converter Configuration.
 *
 * @author Siegfried Puchbauer
 * @see org.grails.web.converters.configuration.ChainedConverterConfiguration
 */
@SuppressWarnings("rawtypes")
public class ImmutableConverterConfiguration<C extends Converter> implements ConverterConfiguration<C> {

    protected final List<ObjectMarshaller<C>> marshallers;

    private final String encoding;

    private final Converter.CircularReferenceBehaviour circularReferenceBehaviour;

    private final boolean prettyPrint;

    private ProxyHandler proxyHandler;

    private final boolean cacheObjectMarshallerByClass;

    public ImmutableConverterConfiguration(ConverterConfiguration<C> cfg) {
        this(cfg, new DefaultProxyHandler());
    }

    public ImmutableConverterConfiguration(ConverterConfiguration<C> cfg, ProxyHandler proxyHandler) {
        marshallers = Collections.unmodifiableList(cfg.getOrderedObjectMarshallers());
        encoding = cfg.getEncoding();
        prettyPrint = cfg.isPrettyPrint();
        cacheObjectMarshallerByClass = cfg.isCacheObjectMarshallerByClass();
        circularReferenceBehaviour = cfg.getCircularReferenceBehaviour();
        this.proxyHandler = proxyHandler;
    }

    /**
     * @see ConverterConfiguration#getMarshaller(Object)
     */
    public ObjectMarshaller<C> getMarshaller(Object o) {
        for (ObjectMarshaller<C> om : marshallers) {
            if (om.supports(o)) {
                return om;
            }
        }
        return null;
    }

    /**
     * @see ConverterConfiguration#getEncoding()
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * @see ConverterConfiguration#getCircularReferenceBehaviour()
     */
    public Converter.CircularReferenceBehaviour getCircularReferenceBehaviour() {
        return circularReferenceBehaviour;
    }

    /**
     * @see ConverterConfiguration#isPrettyPrint()
     */
    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public List<ObjectMarshaller<C>> getOrderedObjectMarshallers() {
        return marshallers;
    }

    public ProxyHandler getProxyHandler() {
        return proxyHandler;
    }

    public boolean isCacheObjectMarshallerByClass() {
        return cacheObjectMarshallerByClass;
    }
}

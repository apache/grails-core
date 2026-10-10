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

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import grails.util.Environment
import org.grails.web.converters.Converter
import org.grails.web.converters.exceptions.ConverterException
import org.grails.web.converters.marshaller.ObjectMarshaller

/**
 * An immutable ConverterConfiguration which chains the lookup calls for ObjectMarshallers
 * for performance reasons.
 *
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 *
 * @since 1.1
 */
@SuppressWarnings('rawtypes')
@CompileStatic
class ChainedConverterConfiguration<C extends Converter> implements ConverterConfiguration<C> {

    private List<ObjectMarshaller<C>> marshallerList
    private ChainedObjectMarshaller<C> root
    private final String encoding
    private final Converter.CircularReferenceBehaviour circularReferenceBehaviour
    private final boolean prettyPrint
    private ProxyHandler proxyHandler
    private final boolean cacheObjectMarshallerByClass
    private Map<Integer, ObjectMarshaller<C>> objectMarshallerForClassCache
    private final boolean developmentMode = Environment.isDevelopmentMode()
    private final ObjectMarshaller<C> nullHolder = new ObjectMarshaller<C>() {
        boolean supports(Object object) {
            return false
        }

        void marshalObject(Object object, C converter) throws ConverterException {
        }
    }

    ChainedConverterConfiguration(ConverterConfiguration<C> cfg) {
        this(cfg, new DefaultProxyHandler())
    }

    ChainedConverterConfiguration(ConverterConfiguration<C> cfg, ProxyHandler proxyHandler) {
        marshallerList = cfg.getOrderedObjectMarshallers()
        this.proxyHandler = proxyHandler

        encoding = cfg.getEncoding()
        prettyPrint = cfg.isPrettyPrint()
        cacheObjectMarshallerByClass = cfg.isCacheObjectMarshallerByClass()
        if (cacheObjectMarshallerByClass) {
            objectMarshallerForClassCache = new ConcurrentHashMap<>()
        }
        circularReferenceBehaviour = cfg.getCircularReferenceBehaviour()

        List<ObjectMarshaller<C>> oms = new ArrayList<>(marshallerList)
        Collections.reverse(oms)
        ChainedObjectMarshaller<C> prev = null
        for (ObjectMarshaller<C> om : oms) {
            prev = new ChainedObjectMarshaller<>(om, prev)
        }
        root = prev
    }

    ObjectMarshaller<C> getMarshaller(Object o) {
        ObjectMarshaller<C> marshaller = null

        Integer cacheKey = null
        if (!developmentMode && cacheObjectMarshallerByClass && o != null) {
            cacheKey = System.identityHashCode(o.getClass())
            marshaller = objectMarshallerForClassCache.get(cacheKey)
            if (!nullHolder.is(marshaller) && marshaller != null && !marshaller.supports(o)) {
                marshaller = null
            }
        }
        if (marshaller == null) {
            marshaller = root.findMarhallerFor(o)
            if (cacheKey != null) {
                objectMarshallerForClassCache.put(cacheKey, marshaller != null ? marshaller : nullHolder)
            }
        }
        return !nullHolder.is(marshaller) ? marshaller : null
    }

    String getEncoding() {
        return encoding
    }

    Converter.CircularReferenceBehaviour getCircularReferenceBehaviour() {
        return circularReferenceBehaviour
    }

    boolean isPrettyPrint() {
        return prettyPrint
    }

    List<ObjectMarshaller<C>> getOrderedObjectMarshallers() {
        return marshallerList
    }

    @SuppressWarnings('hiding')
    class ChainedObjectMarshaller<C extends Converter> implements ObjectMarshaller<C> {

        private ObjectMarshaller<C> om
        private ChainedObjectMarshaller<C> next

        ChainedObjectMarshaller(ObjectMarshaller<C> om, ChainedObjectMarshaller<C> next) {
            this.om = om
            this.next = next
        }

        ObjectMarshaller<C> findMarhallerFor(Object o) {
            if (supports(o)) {
                return om
            }

            return next != null ? next.findMarhallerFor(o) : null
        }

        boolean supports(Object object) {
            return om.supports(object)
        }

        void marshalObject(Object object, C converter) throws ConverterException {
            om.marshalObject(object, converter)
        }

    }

    ProxyHandler getProxyHandler() {
        return proxyHandler
    }

    boolean isCacheObjectMarshallerByClass() {
        return cacheObjectMarshallerByClass
    }

}

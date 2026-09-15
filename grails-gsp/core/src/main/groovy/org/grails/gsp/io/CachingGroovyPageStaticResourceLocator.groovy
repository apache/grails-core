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
package org.grails.gsp.io

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import groovy.transform.CompileStatic
import org.springframework.core.io.Resource

import grails.util.CacheEntry

/**
 * Extends {@link GroovyPageStaticResourceLocator} adding caching of the result
 * of {@link GroovyPageStaticResourceLocator#findResourceForURI(String)}.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class CachingGroovyPageStaticResourceLocator extends GroovyPageStaticResourceLocator {

    private ConcurrentMap<String, CacheEntry<Resource>> uriResolveCache = new ConcurrentHashMap<>()
    private long cacheTimeout = -1

    @Override
    Resource findResourceForURI(final String uri) {
        Callable<Resource> updater = new Callable<Resource>() {
            Resource call() {
                Resource resource = superFindResourceForURI(uri)
                if (resource == null) {
                    resource = NULL_RESOURCE
                }
                return resource
            }
        }

        Resource resource = CacheEntry.getValue(uriResolveCache, uri, cacheTimeout, updater)
        return resource == NULL_RESOURCE ? null : resource
    }

    /**
     * Groovy has no {@code OuterClass.super.method()} syntax for reaching this class's own
     * superclass from the anonymous {@link Callable} above, unlike Java's qualified super. This
     * gives that call a name the anonymous class can invoke unambiguously.
     */
    private Resource superFindResourceForURI(String uri) {
        return super.findResourceForURI(uri)
    }

    long getCacheTimeout() {
        return cacheTimeout
    }

    void setCacheTimeout(long cacheTimeout) {
        this.cacheTimeout = cacheTimeout
    }

}

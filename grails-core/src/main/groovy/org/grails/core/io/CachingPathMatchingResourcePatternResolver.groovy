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

package org.grails.core.io

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * @author Graeme Rocher
 * @since 3.0.12
 */
@CompileStatic
class CachingPathMatchingResourcePatternResolver extends PathMatchingResourcePatternResolver {

    public static final CachingPathMatchingResourcePatternResolver INSTANCE = new CachingPathMatchingResourcePatternResolver();

    private CachingPathMatchingResourcePatternResolver() {}

    CachingPathMatchingResourcePatternResolver(ResourceLoader parent) {
        super(parent)
    }

    @Memoized(maxCacheSize = 20)
    protected Set<Resource> doFindAllClassPathResources(String path) throws IOException {
        return super.doFindAllClassPathResources(path)
    }

    @Memoized(maxCacheSize = 20)
    protected Resource[] findPathMatchingResources(String locationPattern) throws IOException {
        return super.findPathMatchingResources(locationPattern)
    }
}

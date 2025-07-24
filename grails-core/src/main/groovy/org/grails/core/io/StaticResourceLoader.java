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
package org.grails.core.io;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

/**
 * A ResourceLoader that loads resources from a statically defined base resource.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
public class StaticResourceLoader implements ResourceLoader {
    private static final Logger LOG = LoggerFactory.getLogger(StaticResourceLoader.class);
    private Resource baseResource;

    public void setBaseResource(Resource baseResource) {
        this.baseResource = baseResource;
    }

    public Resource getResource(String location) {
        Assert.state(baseResource != null, "Property [baseResource] not set!");

        if(LOG.isDebugEnabled()) {
            LOG.debug("Loading resource for path {} from base resource {}", location, baseResource);
        }
        try {
            Resource resource = baseResource.createRelative(location);
            if(LOG.isDebugEnabled() && resource.exists()) {
                LOG.debug("Found resource for path {} from base resource {}", location, baseResource);
            }
            return resource;
        }
        catch (IOException e) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Error loading resource for path: " + location, e);
            }
            return null;
        }
    }

    public ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}

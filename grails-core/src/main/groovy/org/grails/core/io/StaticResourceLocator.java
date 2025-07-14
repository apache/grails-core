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

import org.springframework.core.io.Resource;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * A static resource locator that uses an internal map to locate resources. Used largely for testing.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class StaticResourceLocator implements ResourceLocator {

    private Map<String, Resource> classToResourceMap = new HashMap<String, Resource>();

    public void setSearchLocation(String searchLocation) {
        // do nothing
    }

    public void setSearchLocations(Collection<String> searchLocations) {
        // do nothing
    }

    public Resource findResourceForURI(String uri) {
        return null; // TODO implement static resource location
    }

    public Resource findResourceForClassName(String className) {
        return classToResourceMap.get(className);
    }

    public void addClassResource(String className, Resource res) {
        classToResourceMap.put(className, res);
    }
}

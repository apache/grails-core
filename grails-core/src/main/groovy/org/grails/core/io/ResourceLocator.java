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

/**
 * Used to locate resources at development or production time.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public interface ResourceLocator {

    /**
     * The basic location from which to conduct the search. At development time this is the
     * base directory, during production this would be the servlet root
     *
     * @param searchLocation The search location
     */
    void setSearchLocation(String searchLocation);

    /**
     * Multiple locations to search. See #setSearchLocation
     * @param searchLocations The locations to search
     */
    void setSearchLocations(Collection<String> searchLocations);

    /**
     * Finds a resource for the given URI
     * @param uri The URI
     * @return The resource or null if it doesn't exist
     */
    Resource findResourceForURI(String uri);

    /**
     * Finds the .groovy file or .java file for a given class from a Grails project.
     *
     * Note that this method will return null in production since sources are not packaged unless an
     * appropriate search location is specified to locate the resource
     *
     * @param className The class name
     * @return The resource or null
     */
    Resource findResourceForClassName(String className);
}

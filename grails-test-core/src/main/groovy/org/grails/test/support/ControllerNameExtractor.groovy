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
package org.grails.test.support

import grails.util.GrailsNameUtils

class ControllerNameExtractor {
    /**
     * Derive the controller name from the given class name using the list of given suffixes,
     * typically ['Test', 'Tests', 'Spec', 'Specification']. Given something like
     *
     * 'com.triu.TheControllerSpec' -> 'the'
     * 'TheControllerTests' -> 'the'
     * 'TheControllerIntegrationTests' -> 'the'
     * 'TheTests' -> null
     *
     * @param testClassName
     * @param testClassSuffixes
     * @return the controller name or null if nothing could be derived
     */
    static String extractControllerNameFromTestClassName(String testClassName, String[] testClassSuffixes) {
        def patternSuffix = testClassSuffixes ? "(${testClassSuffixes.join('$|')})" : ""
        def pattern = ~"(\\w*)Controller\\w*${patternSuffix}"

        def matches = testClassName =~ pattern

        //noinspection GroovyAssignabilityCheck
        matches && matches[0] && matches[0][1] ? GrailsNameUtils.getPropertyName(matches[0][1]) : null
    }
}

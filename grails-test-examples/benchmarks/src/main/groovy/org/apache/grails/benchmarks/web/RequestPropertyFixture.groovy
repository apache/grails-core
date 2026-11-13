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
package org.apache.grails.benchmarks.web

import jakarta.servlet.http.HttpServletRequest

class RequestPropertyFixture {

    static DynamicRequestPropertyReader createReader() {
        new DynamicRequestPropertyReader()
    }
}

/**
 * Reads properties off an {@code HttpServletRequest} the way application Groovy code does.
 *
 * Deliberately <em>not</em> statically compiled: the point of the benchmark is the dynamic call
 * site and the metaclass lookup behind {@code HttpServletRequestExtension}.
 */
class DynamicRequestPropertyReader {

    /**
     * {@code request.someAttribute} - an unknown property, which falls through the metaclass to
     * {@code HttpServletRequestExtension} and ends up as an attribute read.
     */
    Object readUnknownProperty(HttpServletRequest request) {
        request.someAttribute
    }

    /** {@code request.method} - a property backed by a real getter on the request. */
    Object readGetterBackedProperty(HttpServletRequest request) {
        request.method
    }

    /** The explicit, non-dynamic equivalent of {@link #readUnknownProperty}, called from Groovy. */
    Object readAttributeDirectly(HttpServletRequest request) {
        request.getAttribute('someAttribute')
    }
}

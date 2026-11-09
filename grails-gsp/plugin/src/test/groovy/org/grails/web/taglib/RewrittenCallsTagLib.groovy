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
package org.grails.web.taglib

import grails.gsp.TagLib

/**
 * Calls other tags in each of the shapes the rewriter distinguishes, so that what it rewrites and what
 * it leaves alone are both exercised through real rendering.
 */
@TagLib
class RewrittenCallsTagLib {

    static namespace = 'rewrite'

    /** A namespaced call with attributes: rewritten. */
    def viaNamespace(Map attrs) {
        out << g.link(controller: 'book')
    }

    /** A namespaced call carrying a body: rewritten. */
    def withBody(Map attrs) {
        out << g.link(controller: 'book') { 'inside' }
    }

    /** A namespace no compiled tag library declares: left to resolve at runtime. */
    def viaUnknownNamespace(Map attrs) {
        out << 'fallback'
    }

    /** Attributes assembled at runtime, so the call shape is not evident: left alone. */
    def viaComputedAttributes(Map attrs) {
        Map linkAttrs = [controller: 'book']
        out << g.link(linkAttrs)
    }
}

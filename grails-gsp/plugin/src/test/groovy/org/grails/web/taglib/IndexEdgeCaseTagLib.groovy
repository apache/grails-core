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

import grails.gsp.NotATag
import grails.gsp.Tag
import grails.gsp.TagLib

/**
 * Exercises the cases where "is this a tag" is decided by something other than the plain
 * {@code (Map)} / {@code (Map, Closure)} shape, so that the compile-time index and the runtime
 * agree on all of them.
 */
@TagLib
class IndexEdgeCaseTagLib extends BaseEdgeTagLib {

    static namespace = 'edge'

    /** Conventional attributes-only tag. */
    def plain(Map attrs) { 'plain' }

    /** Conventional tag taking a body. */
    def withBody(Map attrs, Closure body) { 'withBody' }

    /** Conventional shape, but explicitly excluded. */
    @NotATag
    def excluded(Map attrs) { 'excluded' }

    /** Unconventional shape, but explicitly included, with attributes bound by parameter name. */
    @Tag
    def annotated(Map attrs, String code) { code }

    /** Untyped attributes are not assignable to Map and so are not dispatchable. */
    def untyped(attrs) { 'untyped' }

    /** An ordinary helper that happens to live on the tag library. */
    String helper(String a, int b) { a }

    /**
     * A Map parameter not named {@code attrs}. Runtime requires the name to be {@code attrs} whenever
     * parameter names are retained, which this build does.
     */
    def renamedAttrs(Map options) { 'renamedAttrs' }
}

/** A tag declared on a base class, which reflection's declared-only scan does not see. */
abstract class BaseEdgeTagLib {
    def inherited(Map attrs) { 'inherited' }
}

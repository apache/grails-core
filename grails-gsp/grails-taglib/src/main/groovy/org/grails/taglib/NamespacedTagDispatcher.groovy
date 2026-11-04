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
package org.grails.taglib

import groovy.transform.CompileStatic

import grails.core.GrailsApplication

/**
 * Allows dispatching to namespaced tag libraries and is used within controllers and tag libraries
 * to allow namespaced tags to be invoked as methods (eg. g.link(action:'foo')).
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class NamespacedTagDispatcher extends GroovyObjectSupport {

    protected String namespace
    protected GrailsApplication application
    protected Class type
    protected TagLibraryLookup lookup

    NamespacedTagDispatcher(String ns, Class callingType, GrailsApplication application, TagLibraryLookup lookup) {
        this.namespace = ns
        this.application = application
        this.lookup = lookup
        this.type = callingType ?: this.getClass()
    }

    /**
     * Every dispatcher used to be given its own ExpandoMetaClass carrying a method for each tag in the
     * namespace, built and populated as the dispatcher was constructed. Tags are dispatched through
     * the lookup instead, so no metaclass is created or written to here.
     */
    def methodMissing(String name, Object args) {
        TagLibraryMetaUtils.methodMissingForTagLib(getMetaClass(), type, lookup, namespace, name, args, false)
    }
}

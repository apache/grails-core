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
package org.grails.gsp

import groovy.transform.CompileStatic

import grails.util.GrailsMetaClassUtils
import org.grails.taglib.TagLibraryLookup

@CompileStatic
class GroovyPagesMetaUtils {

    static void registerMethodMissingForGSP(Class gspClass, TagLibraryLookup gspTagLibraryLookup) {
        registerMethodMissingForGSP(GrailsMetaClassUtils.getExpandoMetaClass(gspClass), gspTagLibraryLookup)
    }

    /**
     * Nothing is installed onto a page's metaclass any more.
     *
     * <p>A page used to be given methodMissing, a method for each tag and a property for each
     * namespace as it was compiled. GroovyPage declares methodMissing itself and resolves a namespace
     * through getProperty, so the tags reachable from a page are the same without any of those writes.
     *
     * @param emc the page's metaclass, no longer modified
     * @param gspTagLibraryLookup the tag libraries, resolved through at dispatch instead
     * @deprecated Pages resolve tags without their metaclass being written to.
     */
    @Deprecated
    static void registerMethodMissingForGSP(final MetaClass emc, final TagLibraryLookup gspTagLibraryLookup) {
    }

}

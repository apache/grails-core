/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gsp
import grails.util.Environment
import grails.util.GrailsMetaClassUtils
import groovy.transform.CompileStatic
import org.grails.taglib.TagLibraryLookup
import org.grails.taglib.TagLibraryMetaUtils

@CompileStatic
class GroovyPagesMetaUtils {

    static void registerMethodMissingForGSP(Class gspClass, TagLibraryLookup gspTagLibraryLookup) {
        registerMethodMissingForGSP(GrailsMetaClassUtils.getExpandoMetaClass(gspClass), gspTagLibraryLookup)
    }

    static void registerMethodMissingForGSP(final MetaClass emc, final TagLibraryLookup gspTagLibraryLookup) {
        if(gspTagLibraryLookup==null) return
        final boolean addMethodsToMetaClass = !Environment.isDevelopmentMode()

        GroovyObject mc = (GroovyObject)emc
        synchronized(emc) {
            mc.setProperty("methodMissing", { String name, Object args ->
                TagLibraryMetaUtils.methodMissingForTagLib(emc, emc.getTheClass(), gspTagLibraryLookup, GroovyPage.DEFAULT_NAMESPACE, name, args, addMethodsToMetaClass)
            })
        }
        TagLibraryMetaUtils.registerTagMetaMethods(emc, gspTagLibraryLookup, GroovyPage.DEFAULT_NAMESPACE)
        TagLibraryMetaUtils.registerNamespaceMetaProperties(emc, gspTagLibraryLookup)
    }


}

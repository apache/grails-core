/*
 * Copyright 2014 original authors
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
package grails.artefact.gsp

import grails.util.Environment
import grails.util.GrailsMetaClassUtils
import grails.web.api.WebAttributes
import groovy.transform.CompileStatic
import org.grails.taglib.NamespacedTagDispatcher
import org.grails.taglib.TagLibraryLookup
import org.grails.taglib.TagLibraryMetaUtils
import org.grails.taglib.TagOutput
import org.grails.taglib.encoder.WithCodecHelper
import org.springframework.beans.factory.annotation.Autowired

/**
 * A trait that adds the ability invoke tags to any class
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
trait TagLibraryInvoker extends WebAttributes{


    private TagLibraryLookup tagLibraryLookup
    private boolean developmentMode = Environment.isDevelopmentMode();

    @Autowired(required = false)
    void setTagLibraryLookup(TagLibraryLookup tagLibraryLookup) {
        this.tagLibraryLookup = tagLibraryLookup
    }

    TagLibraryLookup getTagLibraryLookup() {
        def lookup = this.tagLibraryLookup
        if(lookup == null) {
            def applicationContext = getGrailsApplication()?.mainContext
            if(applicationContext?.containsBean("gspTagLibraryLookup")) {
                lookup = applicationContext?.getBean("gspTagLibraryLookup", TagLibraryLookup)
                setTagLibraryLookup(lookup)
            }
        }
        return lookup
    }

    String getTaglibNamespace() {
        TagOutput.DEFAULT_NAMESPACE
    }


    /**
     * Method missing implementation that handles tag invocation by method name
     *
     * @param instance The instance
     * @param methodName The method name
     * @param argsObject The arguments
     * @return The result
     */
    Object methodMissing(String methodName, Object argsObject) {
        Object[] args = argsObject instanceof Object[] ? (Object[])argsObject : [argsObject] as Object[]
        if (shouldHandleMethodMissing(methodName, args)) {
            TagLibraryLookup lookup = getTagLibraryLookup()
            if (lookup) {
                def usedNamespace = getTaglibNamespace()
                GroovyObject tagLibrary = lookup.lookupTagLibrary(usedNamespace, methodName)
                if (tagLibrary == null) {
                    tagLibrary = lookup.lookupTagLibrary(TagOutput.DEFAULT_NAMESPACE, methodName);
                    usedNamespace = TagOutput.DEFAULT_NAMESPACE;
                }

                if (tagLibrary) {
                    if (!developmentMode) {
                        MetaClass thisMc = GrailsMetaClassUtils.getMetaClass(this)
                        TagLibraryMetaUtils.registerMethodMissingForTags(thisMc, lookup, usedNamespace, methodName)
                    }
                    return tagLibrary.invokeMethod(methodName, args)
                }
            }
        }
        throw new MissingMethodException(methodName, this.getClass(), args)
    }

    private boolean shouldHandleMethodMissing(String methodName, Object[] args) {
        if("render".equals(methodName)) {
            // don't add any new metamethod if an existing render method exists, see GRAILS-11581
            return !this.respondsTo("render")
        } else {
            return true
        }
    }

    /**
     * Looks up namespaces on missing property
     *
     * @param instance The instance
     * @param propertyName The property name
     * @return The namespace or a MissingPropertyException
     */
     Object propertyMissing(String propertyName) {
        TagLibraryLookup lookup = getTagLibraryLookup()
        NamespacedTagDispatcher namespacedTagDispatcher = lookup?.lookupNamespaceDispatcher(propertyName)
        if (namespacedTagDispatcher) {
            if (!developmentMode) {
                TagLibraryMetaUtils.registerPropertyMissingForTag(GrailsMetaClassUtils.getMetaClass(this),propertyName, namespacedTagDispatcher)
            }
            return namespacedTagDispatcher
        }

        throw new MissingPropertyException(propertyName, this.getClass())
    }

    /**
     * @see {@link WithCodecHelper#withCodec(grails.core.GrailsApplication, java.lang.Object, groovy.lang.Closure)}
     */
    def <T> T withCodec(Object codecInfo, Closure<T> body) {
        return WithCodecHelper.withCodec(getGrailsApplication(), codecInfo, body)
    }


}
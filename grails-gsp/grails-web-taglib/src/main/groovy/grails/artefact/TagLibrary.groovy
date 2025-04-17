/*
 * Copyright 2014-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.artefact

import grails.artefact.gsp.TagLibraryInvoker
import grails.util.Environment
import grails.util.GrailsMetaClassUtils
import grails.web.api.ServletAttributes
import grails.web.api.WebAttributes
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.InvokerHelper
import org.grails.buffer.GrailsPrintWriter
import org.grails.encoder.Encoder
import org.grails.taglib.encoder.OutputEncodingStack
import org.grails.taglib.encoder.WithCodecHelper
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.taglib.TagLibraryLookup
import org.grails.taglib.TagOutput
import org.grails.taglib.TemplateVariableBinding
import org.grails.web.taglib.WebRequestTemplateVariableBinding
import org.grails.taglib.GrailsTagException
import org.grails.taglib.TagLibraryMetaUtils
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.web.context.request.RequestAttributes

import jakarta.annotation.PostConstruct
/**
 * A trait that makes a class into a GSP tag library
 *
 * @since 3.0
 * @author Jeff Brown
 * @author Graeme Rocher
 */
@CompileStatic
trait TagLibrary implements WebAttributes, ServletAttributes, TagLibraryInvoker {

    private Encoder rawEncoder

    @PostConstruct
    void initializeTagLibrary() {
        if(!Environment.isDevelopmentMode()) {
            TagLibraryMetaUtils.enhanceTagLibMetaClass(GrailsMetaClassUtils.getExpandoMetaClass(getClass()), getTagLibraryLookup(), getTaglibNamespace())
        }
    }

    @CompileDynamic
    def raw(Object value) {
        if (rawEncoder == null) {
            rawEncoder = WithCodecHelper.lookupEncoder(grailsApplication, "Raw")
            if(rawEncoder == null)
                return InvokerHelper.invokeMethod(value, "encodeAsRaw", null)
        }
        return rawEncoder.encode(value)
    }

    /**
     * Throws a GrailsTagException
     *
     * @param message The error message
     */
    void throwTagError(String message) {
        throw new GrailsTagException(message)
    }

    String getTaglibNamespace() {
        if(hasProperty('namespace')) {
            return ((GroovyObject)this).getProperty('namespace')
        }
        return TagOutput.DEFAULT_NAMESPACE
    }

    /**
     * Obtains the page scope instance
     *
     * @return  The page scope instance
     */
    TemplateVariableBinding getPageScope() {
        GrailsWebRequest webRequest = getWebRequest()
        TemplateVariableBinding binding = (TemplateVariableBinding) webRequest.getAttribute(GrailsApplicationAttributes.PAGE_SCOPE, RequestAttributes.SCOPE_REQUEST)
        if (binding == null) {
            binding = new TemplateVariableBinding(new WebRequestTemplateVariableBinding(webRequest))
            binding.root = true
            webRequest.setAttribute(GrailsApplicationAttributes.PAGE_SCOPE, binding, RequestAttributes.SCOPE_REQUEST)
        }
        binding
    }

    /**
     * Obtains the currently output writer

     * @return The writer to use
     */
    GrailsPrintWriter getOut() {
        OutputEncodingStack.currentStack().taglibWriter
    }

    /**
     * Sets the current output writer
     * @param newOut The new output writer
     */
    void setOut(Writer newOut) {
        OutputEncodingStack.currentStack().push(newOut,true)
    }
    

    /**
     * Property missing implementation that looks up tag library namespaces or tags in the default namespace
     *
     * @param name The property name
     * @return A tag namespace or a tag in the default namespace
     *
     * @throws MissingPropertyException When no tag namespace or tag is found
     */
    Object propertyMissing(String name) {
        TagLibraryLookup gspTagLibraryLookup = getTagLibraryLookup();
        if (gspTagLibraryLookup != null) {

            Object result = gspTagLibraryLookup.lookupNamespaceDispatcher(name);
            if (result == null) {
                String namespace = getTaglibNamespace()
                GroovyObject tagLibrary = gspTagLibraryLookup.lookupTagLibrary(namespace, name);
                if (tagLibrary == null) {
                    tagLibrary = gspTagLibraryLookup.lookupTagLibrary(TagOutput.DEFAULT_NAMESPACE, name);
                }

                if (tagLibrary != null) {
                    Object tagProperty = tagLibrary.getProperty(name);
                    if (tagProperty instanceof Closure) {
                        result = ((Closure<?>)tagProperty).clone();
                    }
                }
            }

            if (result != null && !Environment.isDevelopmentMode()) {
                MetaClass mc = GrailsMetaClassUtils.getExpandoMetaClass(getClass())

                // Register the property for the already-existing singleton instance of the taglib
                TagLibraryMetaUtils.registerPropertyMissingForTag(this.metaClass, name, result)

                // Register the property for the ExpandoMetaClass so that other tag libs that inherit from it benefit
                TagLibraryMetaUtils.registerPropertyMissingForTag(mc, name, result);
            }

            if (result != null) {
                return result;
            }
        }

        throw new MissingPropertyException(name, this.getClass());
    }

}

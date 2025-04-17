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
package org.grails.taglib
import grails.core.GrailsApplication
import grails.util.Environment
import groovy.transform.CompileStatic
import org.grails.taglib.encoder.OutputContextLookupHelper

@CompileStatic
class TemplateNamespacedTagDispatcher extends NamespacedTagDispatcher {
    public static final String TEMPLATE_NAMESPACE = "tmpl"

    private boolean developmentMode = Environment.current.isDevelopmentMode()

    TemplateNamespacedTagDispatcher(Class callingType, GrailsApplication application, TagLibraryLookup lookup) {
        super(TEMPLATE_NAMESPACE, callingType, application, lookup)
    }

    def methodMissing(String name, Object args) {
        ((GroovyObject)getMetaClass()).setProperty(name, { Object[] varArgs ->
            callRender(argsToAttrs(name, varArgs), filterBodyAttr(varArgs))
        })
        callRender(argsToAttrs(name, args), filterBodyAttr(args))
    }
    
    protected void registerTagMetaMethods(ExpandoMetaClass emc) {
        
    }

    protected callRender(Map attrs, Object body) {
        TagOutput.captureTagOutput(lookup, TagOutput.DEFAULT_NAMESPACE, 'render', attrs, body, OutputContextLookupHelper.lookupOutputContext() )
    }

    protected Map argsToAttrs(String name, Object args) {
        Map<String, Object> attr = [:]
        attr.template = name
        if (args instanceof Object[]) {
            Object[] tagArgs = ((Object[])args)
            if (tagArgs.length > 0 && tagArgs[0] instanceof Map) {
                Map<String, Object> modelMap = (Map<String, Object>)tagArgs[0]
                Object encodeAs = modelMap.remove(TagOutput.ENCODE_AS_ATTRIBUTE_NAME)
                if (encodeAs != null) {
                    attr.put(TagOutput.ENCODE_AS_ATTRIBUTE_NAME, encodeAs)
                }
                attr.put("model", modelMap)
            }
        }
        attr
    }
    
    protected Object filterBodyAttr(Object args) {
        if (args instanceof Object[]) {
            Object[] tagArgs = ((Object[])args)
            if (tagArgs.length > 0) {
                for(Object arg : tagArgs) {
                    if(!(arg instanceof Map)) {
                        return arg
                    }
                }
            }
        }
        return null
    }
}

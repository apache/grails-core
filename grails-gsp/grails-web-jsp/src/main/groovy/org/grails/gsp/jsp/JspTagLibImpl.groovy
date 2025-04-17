/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.gsp.jsp

import groovy.transform.CompileStatic
import org.grails.buffer.FastStringPrintWriter
import org.springframework.util.Assert

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class JspTagLibImpl implements JspTagLib {
    private String uri
    private Map<String, JspTagImpl> tags = [:]

    JspTagLibImpl(String uri, Map<String, String> tagClasses, ClassLoader classLoader) {
        Assert.notNull uri, "The URI of the tag library must be specified!"
        this.uri = uri
        tagClasses.each { String tagName, String className ->
            tags[tagName] = new JspTagImpl(className, classLoader)
        }
    }

    JspTag getTag(String name) {
        return tags[name]
    }

    String getURI() {
        return uri
    }

    /**
     * Overrides invoke method so tags can be invoked as methods
     */
    Object invokeMethod(String name, Object argsParam) {
        JspTag tag = getTag(name)

        if (tag) {
            Object[] args = (Object[])argsParam 
            if(args == null || args.length==0) {
                 args = [[:]] as Object[]
            }

            Map<String, Object> attrs = args[0] instanceof Map ? (Map)args[0] : [:]
            Closure body = args[0] instanceof Closure ? (Closure)args[0] : null
            if (args.size() > 1) body = args[1] instanceof Closure ? (Closure)args[1] : null
            if (body == null && args.size() > 1) {
                body = { args[1] }
            }
            else {
                body = {}
            }

            def sw = new FastStringPrintWriter()
            tag.doTag(sw, attrs, body)
            return sw.buffer
        }

        return super.invokeMethod(name, argsParam)
    }
}

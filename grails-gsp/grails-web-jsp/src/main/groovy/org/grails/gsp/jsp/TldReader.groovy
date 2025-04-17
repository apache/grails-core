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
import groovy.transform.TypeCheckingMode
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.grails.io.support.SpringIOUtils

/**
 * A SAX parser implementation that reads the contents of a tag library definition (TLD) into two properties
 * called tags and listeners (for the tag listeners)
 */
@CompileStatic
class TldReader {
    private static final Log log=LogFactory.getLog(TldReader.class)
    final Map<String,String> tags = [:]
    final List<String> listeners = []
    String uri
        
    public TldReader(InputStream inputStream) {
        inputStream.withStream { 
            init(new BufferedInputStream(inputStream))
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private init(InputStream inputStream) {
        def rootNode = SpringIOUtils.createXmlSlurper().parse(inputStream)
        uri = rootNode.uri.text()
        rootNode.tag.each { tag ->
            String tagName = tag.name.text()
            String className = tag.'tag-class'.text() ?: tag.'tagclass'.text()
            tags[tagName] = className
        }
        rootNode.'listener-class'.each { listenerClassNode ->
            listeners << listenerClassNode.text()
        }
    }
}

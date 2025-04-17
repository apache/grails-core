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
import org.grails.io.support.SpringIOUtils

/**
 * reads the tag library definitions from a web.xml file
 *
 * @author Graeme Rocher
 */
@CompileStatic
class WebXmlTagLibraryReader {
    /**
     * Contains a map of URI to tag library locations once the handler has read the web.xml file
     */
    Map<String, String> tagLocations = [:]

    public WebXmlTagLibraryReader(InputStream inputStream) {
        inputStream.withStream {
            init(new BufferedInputStream(inputStream))
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private init(InputStream inputStream) {
        def rootNode = SpringIOUtils.createXmlSlurper().parse(inputStream)
        rootNode.taglib.each { taglib ->
            String uri = taglib.'taglib-uri'.text()
            String location =  taglib.'taglib-location'.text()
            tagLocations[uri] = location
        }
    }
}

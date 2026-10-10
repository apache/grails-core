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
package org.grails.core.io

import spock.lang.Specification

import org.springframework.core.io.ContextResource
import org.springframework.core.io.Resource

class PluginPathAwareFileSystemResourceLoaderSpec extends Specification {

    void 'falls back to the resource locator when the file system path does not exist'() {
        given:
        def located = Mock(Resource)
        def resourceLocator = Mock(ResourceLocator)
        resourceLocator.findResourceForURI('/css/main.css') >> located
        def loader = new PluginPathAwareFileSystemResourceLoader()
        loader.resourceLocator = resourceLocator

        when:
        def resource = loader.getResourceByPath('web-app/css/main.css')

        then:
        resource.is(located)
    }

    void 'falls back to a FileSystemContextResource when neither the file system nor the locator has the resource'() {
        given:
        def resourceLocator = Mock(ResourceLocator)
        resourceLocator.findResourceForURI(_) >> null
        def loader = new PluginPathAwareFileSystemResourceLoader()
        loader.resourceLocator = resourceLocator

        when:
        def resource = loader.getResourceByPath('does/not/exist.css')

        then:
        resource instanceof ContextResource
        resource.getFilename() == 'exist.css'
    }

}

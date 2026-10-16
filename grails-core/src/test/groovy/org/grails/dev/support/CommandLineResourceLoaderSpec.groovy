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
package org.grails.dev.support

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import spock.lang.Specification

class CommandLineResourceLoaderSpec extends Specification {

    void 'a /WEB-INF location resolves to a FileSystemResource under ./web-app'() {
        given:
        def loader = new CommandLineResourceLoader()

        when:
        Resource resource = loader.getResource('/WEB-INF/applicationContext.xml')

        then:
        resource instanceof FileSystemResource
        resource.path.endsWith('web-app/WEB-INF/applicationContext.xml')
    }

    void 'a non-/WEB-INF location delegates to the default resource-loading behavior'() {
        given:
        def loader = new CommandLineResourceLoader()

        when:
        Resource resource = loader.getResource('classpath:some/file.txt')

        then:
        !(resource instanceof FileSystemResource) || !resource.path.contains('web-app')
    }
}

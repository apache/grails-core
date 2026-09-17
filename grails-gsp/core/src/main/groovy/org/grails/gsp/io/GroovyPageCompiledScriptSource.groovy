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
package org.grails.gsp.io

import java.security.PrivilegedAction

import groovy.transform.CompileStatic
import org.springframework.core.io.Resource

import org.grails.gsp.GroovyPageMetaInfo

/**
 * Represents a pre-compiled GSP.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class GroovyPageCompiledScriptSource implements GroovyPageScriptSource {

    private String uri
    private Class<?> compiledClass
    private GroovyPageMetaInfo groovyPageMetaInfo
    private PrivilegedAction<Resource> resourceCallable
    private boolean isPublic

    GroovyPageCompiledScriptSource(String uri, String fullPath, Class<?> compiledClass) {
        this.uri = uri
        this.isPublic = GroovyPageResourceScriptSource.isPublicPath(fullPath)
        this.compiledClass = compiledClass
        this.groovyPageMetaInfo = new GroovyPageMetaInfo(compiledClass)
    }

    String getURI() {
        return uri
    }

    /**
     * Whether the GSP is publicly accessible directly, or only usable using internal rendering
     *
     * @return true if it can be rendered publicly
     */
    boolean isPublic() {
        return isPublic
    }

    /**
     * @return The compiled class
     */
    Class<?> getCompiledClass() {
        return compiledClass
    }

    String getScriptAsString() throws IOException {
        throw new UnsupportedOperationException('You cannot retrieve the source of a pre-compiled GSP script: ' + uri)
    }

    boolean isModified() {
        if (resourceCallable == null) {
            return false
        }
        return groovyPageMetaInfo.shouldReload(resourceCallable)
    }

    GroovyPageResourceScriptSource getReloadableScriptSource() {
        if (resourceCallable == null) return null
        Resource resource = groovyPageMetaInfo.checkIfReloadableResourceHasChanged(resourceCallable)
        return resource == null ? null : new GroovyPageResourceScriptSource(uri, resource)
    }

    String suggestedClassName() {
        return compiledClass.getName()
    }

    GroovyPageMetaInfo getGroovyPageMetaInfo() {
        return groovyPageMetaInfo
    }

    void setResourceCallable(PrivilegedAction<Resource> resourceCallable) {
        this.resourceCallable = resourceCallable
    }

}

/*
 * Copyright 2011 SpringSource
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
package org.grails.gsp.io;

import org.grails.gsp.GroovyPageMetaInfo;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.security.PrivilegedAction;

/**
 * Represents a pre-compiled GSP.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class GroovyPageCompiledScriptSource implements GroovyPageScriptSource {
    private String uri;
    private Class<?> compiledClass;
    private GroovyPageMetaInfo groovyPageMetaInfo;
    private PrivilegedAction<Resource> resourceCallable;
    private boolean isPublic;

    public GroovyPageCompiledScriptSource(String uri, String fullPath, Class<?> compiledClass) {
        this.uri = uri;
        this.isPublic = GroovyPageResourceScriptSource.isPublicPath(fullPath);
        this.compiledClass = compiledClass;
        this.groovyPageMetaInfo = new GroovyPageMetaInfo(compiledClass);
    }

    public String getURI() {
        return uri;
    }

    /**
     * Whether the GSP is publicly accessible directly, or only usable using internal rendering
     *
     * @return true if it can be rendered publicly
     */
    public boolean isPublic() {
        return isPublic;
    }

    /**
     * @return The compiled class
     */
    public Class<?> getCompiledClass() {
        return compiledClass;
    }

    public String getScriptAsString() throws IOException {
        throw new UnsupportedOperationException("You cannot retrieve the source of a pre-compiled GSP script: " + uri);
    }

    public boolean isModified() {
        if (resourceCallable == null) {
            return false;
        }
        return groovyPageMetaInfo.shouldReload(resourceCallable);
    }

    public GroovyPageResourceScriptSource getReloadableScriptSource() {
        if (resourceCallable == null) return null;
        Resource resource = groovyPageMetaInfo.checkIfReloadableResourceHasChanged(resourceCallable);
        return resource == null ? null : new GroovyPageResourceScriptSource(uri, resource);
    }

    public String suggestedClassName() {
        return compiledClass.getName();
    }

    public GroovyPageMetaInfo getGroovyPageMetaInfo() {
        return groovyPageMetaInfo;
    }

    public void setResourceCallable(PrivilegedAction<Resource> resourceCallable) {
        this.resourceCallable = resourceCallable;
    }
}

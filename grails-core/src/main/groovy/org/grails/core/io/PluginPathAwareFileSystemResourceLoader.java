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
package org.grails.core.io;

import org.springframework.core.io.ContextResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;

import java.util.Collection;

/**
 * FileSystemResourceLoader capable of understanding paths to plugins via the ResourceLocator interface
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class PluginPathAwareFileSystemResourceLoader extends FileSystemResourceLoader{

    public static final String WEB_APP_DIRECTORY = "web-app";
    ResourceLocator resourceLocator = new DefaultResourceLocator();

    public void setSearchLocations(Collection<String> searchLocations) {
        resourceLocator.setSearchLocations(searchLocations);
    }

    @Override
    protected Resource getResourceByPath(String path) {
        Resource resource = super.getResourceByPath(path);
        if (resource != null && resource.exists()) {
            return resource;
        }

        String resourcePath = path;
        if (resourcePath.startsWith(WEB_APP_DIRECTORY)) {
             resourcePath = resourcePath.substring("web-app".length(),resourcePath.length());
        }
        Resource res = resourceLocator.findResourceForURI(resourcePath);
        if (res != null) {
            return res;
        }
        return new FileSystemContextResource(path);
    }

    /**
     * FileSystemResource that explicitly expresses a context-relative path
     * through implementing the ContextResource interface.
     */
    private static class FileSystemContextResource extends FileSystemResource implements ContextResource {

        public FileSystemContextResource(String path) {
            super(path);
        }

        public String getPathWithinContext() {
            return getPath();
        }
    }
}

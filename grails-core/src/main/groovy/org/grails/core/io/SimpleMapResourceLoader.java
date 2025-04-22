/*
 * Copyright 2011 SpringSource
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
package org.grails.core.io;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple implementation of the ResourceLoader interface that uses a Map to load resources.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class SimpleMapResourceLoader implements ResourceLoader {

    private Map<String, Resource> resources = new ConcurrentHashMap<String, Resource>();

    public Map<String, Resource> getResources() {
        return resources;
    }

    public Resource getResource(String location) {
        return resources.get(location);
    }

    public ClassLoader getClassLoader() {
        return SimpleMapResourceLoader.class.getClassLoader();
    }
}

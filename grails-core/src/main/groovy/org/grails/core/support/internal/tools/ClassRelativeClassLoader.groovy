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

package org.grails.core.support.internal.tools

import groovy.transform.CompileStatic
import org.springframework.core.io.FileSystemResource

import grails.io.IOUtils
import grails.util.BuildSettings

/**
 * A classloader that only finds resources and classes that are in the same jar as the given class
 *
 * For internal use only
 *
 * @author Graeme Rocher
 * @since 3.1.13
 */
@CompileStatic
class ClassRelativeClassLoader extends URLClassLoader {

    ClassRelativeClassLoader(Class targetClass) {
        super(createClassLoaderUrls(targetClass), ClassLoader.getSystemClassLoader())
    }

    private static URL[] createClassLoaderUrls(Class targetClass) {
        URL root = IOUtils.findRootResource(targetClass)
        if (BuildSettings.RESOURCES_DIR != null && BuildSettings.RESOURCES_DIR.exists()) {
            try {
                return [root, new FileSystemResource(BuildSettings.RESOURCES_DIR.getCanonicalFile()).getURL()] as URL[]
            } catch (IOException e) {
                return [root] as URL[]
            }
        }
        else {
            return [root] as URL[]
        }
    }

    @Override
    URL getResource(String name) {
        return findResource(name)
    }

    @Override
    Enumeration<URL> getResources(String name) throws IOException {
        if (''.equals(name)) {
            final URL[] urls = getURLs()
            final int l = urls.length
            return new Enumeration<URL>() {
                int i = 0

                @Override
                boolean hasMoreElements() {
                    return i < l
                }

                @Override
                URL nextElement() {
                    return urls[i++]
                }
            }
        }
        else {
            return findResources(name)
        }
    }

}

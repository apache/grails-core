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

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;

/**
 * Loads from the file system if its not found on the classpath. Useful for mock testing.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class MockResourceLoader extends DefaultResourceLoader {

    @Override
    public Resource getResource(String location) {
        Resource r = super.getResource(location);
        if (!r.exists() && isNotPrefixed(location)) {
            if (!location.startsWith("/")) {
                location = "/" + location;
            }
            r = new FileSystemResource(new File("./web-app/WEB-INF" + location));
        }
        return r;
    }

    private static boolean isNotPrefixed(String location) {
        return !location.startsWith("classpath:") && !location.startsWith("classpath*:") && !location.startsWith("file:");
    }
}

/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.testing.runtime.support;

import grails.config.Config;
import grails.config.Settings;
import grails.core.GrailsApplication;
import grails.core.support.GrailsApplicationAware;
import grails.util.BuildSettings;
import groovy.transform.CompileStatic;
import org.grails.io.support.GrailsResourceUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link org.springframework.core.io.ResourceLoader} implementation
 * that loads GSP views relative to the project base directory for unit tests.
 *
 */
@CompileStatic
public class GroovyPageUnitTestResourceLoader extends DefaultResourceLoader implements GrailsApplicationAware, InitializingBean {

    public static final String WEB_INF_PREFIX = "/WEB-INF/grails-app/views";
    private Map<String,String> groovyPages = new ConcurrentHashMap<String, String>();
    private String basePath;
    private GrailsApplication grailsApplication;

    public GroovyPageUnitTestResourceLoader(Map<String, String> groovyPages) {
        this.groovyPages = groovyPages;
    }

    @Override
    public Resource getResource(String location) {

        if (location.startsWith(WEB_INF_PREFIX)) {
            location = location.substring(WEB_INF_PREFIX.length());
        }
        if (groovyPages.containsKey(location)) {
            return new ByteArrayResource(groovyPages.get(location).getBytes(StandardCharsets.UTF_8), location);
        }
        
        if(basePath == null) {
            String basedir = BuildSettings.BASE_DIR.getAbsolutePath();
            basePath = basedir + File.separatorChar + GrailsResourceUtils.VIEWS_DIR_PATH;
        }

        String path = basePath + location;
        path = makeCanonical(path);
        return new FileSystemResource(path);
    }

    private String makeCanonical(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            return path;
        }
    }

    @Override
    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if(grailsApplication != null) {
            Config config = grailsApplication.getConfig();
            String viewDir = config.getProperty(Settings.GSP_VIEWS_DIR);
            if(viewDir != null) {
                basePath = viewDir;
            }
        }
    }
}

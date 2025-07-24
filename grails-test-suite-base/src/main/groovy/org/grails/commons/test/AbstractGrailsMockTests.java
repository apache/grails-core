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
package org.grails.commons.test;

import java.io.IOException;

import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovyClassLoader;
import groovy.test.GroovyTestCase;
import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;

import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.mock.web.MockServletContext;
import org.springframework.util.ClassUtils;

import grails.core.DefaultGrailsApplication;
import grails.core.GrailsApplication;
import org.grails.config.PropertySourcesConfig;
import org.grails.support.MockApplicationContext;

/**
 * Abstract simple test harness for testing Grails Applications that just loads
 * the parsed classes into the GrailsApplication instance.
 *
 * @author Graeme Rocher
 */
public abstract class AbstractGrailsMockTests extends GroovyTestCase {

    /**
     * A GroovyClassLoader instance
     */
    public GroovyClassLoader gcl = new GroovyClassLoader();
    /**
     * The GrailsApplication instance created during setup
     */
    public DefaultGrailsApplication ga;
    public MockApplicationContext ctx;

    @Override
    protected final void setUp() throws Exception {
        ExpandoMetaClass.enableGlobally();
        super.setUp();

        System.out.println("Setting up test");
        ctx = new MockApplicationContext();
        ctx.registerMockBean(GrailsApplication.CLASS_LOADER_BEAN, gcl);
        onSetUp();
        ga = new DefaultGrailsApplication(gcl.getLoadedClasses(),gcl);
        if(ClassUtils.isPresent("Config", gcl)) {
            ConfigObject config = new ConfigSlurper().parse(gcl.loadClass("Config"));
            ga.setConfig(new PropertySourcesConfig(config));
        }
        ga.setApplicationContext(ctx);
        ga.initialise();
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, ga);
        postSetUp();
    }

    @Override
    protected final void tearDown() throws Exception {
        onTearDown();
        ExpandoMetaClass.disableGlobally();
        super.tearDown();
    }

    protected void onSetUp() {
        // implemented in subclasses
    }

    protected void postSetUp() {
        // implemented in subclasses
    }

    protected void onTearDown() {
        // implemented in subclasses
    }

    protected MockServletContext createMockServletContext() {
        return new MockServletContext();
    }

    protected MockApplicationContext createMockApplicationContext() {
        return new MockApplicationContext();
    }

    protected Resource[] getResources(String pattern) throws IOException {
        return new PathMatchingResourcePatternResolver().getResources(pattern);
    }

    protected MessageSource createMessageSource() {
        return new StaticMessageSource();
    }
}

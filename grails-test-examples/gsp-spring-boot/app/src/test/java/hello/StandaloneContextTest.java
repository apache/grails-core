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
package hello;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import org.grails.encoder.CodecLookup;
import org.grails.plugins.sitemesh3.Sitemesh3LayoutTagLib;
import org.grails.plugins.web.taglib.RenderTagLib;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a Spring Boot application rendering views with GSP has in its context, and what it does not.
 *
 * <p>This application is not a Grails application: it has no {@code grails-app/init/Application},
 * and nothing here launches through {@code GrailsApp}. It gets GSP and the beans GSP needs, and none
 * of the Grails plugin runtime.
 */
@SpringBootTest(classes = Application.class)
class StandaloneContextTest {

    @Autowired
    ApplicationContext context;

    /**
     * Every tag library is wired from its own declarations here. A Grails application fills them in
     * by name instead, which hides a dependency that was never declared - and an unencoded attribute
     * value is what a missing codec lookup costs.
     */
    @Test
    void everyTagLibraryIsGivenTheCodecLookupItEncodesWith() {
        assertThat(context.getBean(RenderTagLib.class).getCodecLookup()).isInstanceOf(CodecLookup.class);
        assertThat(context.getBean(Sitemesh3LayoutTagLib.class).getCodecLookup()).isInstanceOf(CodecLookup.class);
    }

    /**
     * The plugin lifecycle runs for a Grails application only, and the core plugin's beans are
     * conditional on the application it builds. This application declares a {@code GrailsApplication}
     * of its own for the GSP beans that need one, which must not bring the rest in behind it.
     */
    @Test
    void thePluginRuntimeIsAbsent() {
        assertThat(context.containsBean("pluginManager")).isFalse();
        assertThat(context.containsBean("grailsConfigProperties")).isFalse();
        assertThat(context.containsBean("classLoader")).isFalse();
    }

}

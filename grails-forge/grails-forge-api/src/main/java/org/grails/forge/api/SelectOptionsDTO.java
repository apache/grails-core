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
package org.grails.forge.api;

import io.micronaut.context.MessageSource;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import org.grails.forge.api.options.ApplicationTypeSelectOptions;
import org.grails.forge.api.options.GormImplSelectOptions;
import org.grails.forge.api.options.JdkVersionSelectOptions;
import org.grails.forge.api.options.LanguageSelectOptions;
import org.grails.forge.api.options.ServletImplSelectOptions;
import org.grails.forge.api.options.TestFrameworkSelectOptions;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.JdkVersion;
import org.grails.forge.options.Language;
import org.grails.forge.options.ServletImpl;
import org.grails.forge.options.TestFramework;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregator for {@link SelectOptionDTO}.
 *
 * @since 6.0.0
 */
@Schema(name = "SelectOptions")
@Introspected
public class SelectOptionsDTO {

    private ApplicationTypeSelectOptions type;

    private JdkVersionSelectOptions jdkVersion;

    private LanguageSelectOptions lang;

    private TestFrameworkSelectOptions test;

    private GormImplSelectOptions gorm;

    private ServletImplSelectOptions servlet;

    SelectOptionsDTO() {
    }

    @Creator
    public SelectOptionsDTO(ApplicationTypeSelectOptions type,
                            JdkVersionSelectOptions jdkVersion,
                            LanguageSelectOptions lang,
                            TestFrameworkSelectOptions test,
                            GormImplSelectOptions gorm,
                            ServletImplSelectOptions servlet) {
        this.type = type;
        this.jdkVersion = jdkVersion;
        this.lang = lang;
        this.test = test;
        this.gorm = gorm;
        this.servlet = servlet;
    }

    @Schema(description = "supported options for application type")
    public ApplicationTypeSelectOptions getType() {
        return type;
    }

    @Schema(description = "supported options for jdk versions")
    public JdkVersionSelectOptions getJdkVersion() {
        return jdkVersion;
    }

    @Schema(description = "supported options for code languages")
    public LanguageSelectOptions getLang() {
        return lang;
    }

    @Schema(description = "supported options for test frameworks")
    public TestFrameworkSelectOptions getTest() {
        return test;
    }

    @Schema(description = "supported options for GORM Implementation")
    public GormImplSelectOptions getGorm() {
        return gorm;
    }

    @Schema(description = "supported options for Servlet Implementation")
    public ServletImplSelectOptions getServlet() {
        return servlet;
    }

    /**
     * Build the options
     *
     * @param messageSource  The {@link io.micronaut.context.MessageSource} to support internationalization
     * @param messageContext The {@link io.micronaut.context.MessageSource.MessageContext}
     * @return the supported options
     */
    public static SelectOptionsDTO make(MessageSource messageSource, MessageSource.MessageContext messageContext) {

        List<ApplicationTypeDTO> applications = Arrays.stream(ApplicationType.values())
                .map(it -> new ApplicationTypeDTO(it, null, messageSource, messageContext))
                .collect(Collectors.toList());

        ApplicationTypeSelectOptions applicationOpts = new ApplicationTypeSelectOptions(
                applications,
                new ApplicationTypeDTO(ApplicationType.DEFAULT_OPTION, null, messageSource, messageContext)
        );

        List<JdkVersionDTO> jdkVersions = Arrays.stream(JdkVersion.values())
                .map(it -> new JdkVersionDTO(it, messageSource, messageContext))
                .collect(Collectors.toList());

        JdkVersionSelectOptions jdkVersionOpts = new JdkVersionSelectOptions(
                jdkVersions,
                new JdkVersionDTO(JdkVersion.DEFAULT_OPTION, messageSource, messageContext)
        );

        List<LanguageDTO> languages = Arrays.stream(Language.values())
                .map(it -> new LanguageDTO(it, messageSource, messageContext))
                .collect(Collectors.toList());

        LanguageSelectOptions languageOpts = new LanguageSelectOptions(
                languages,
                new LanguageDTO(Language.DEFAULT_OPTION, messageSource, messageContext)
        );

        List<TestFrameworkDTO> testFrameworks = Arrays.stream(TestFramework.values())
                .map(it -> new TestFrameworkDTO(it, messageSource, messageContext))
                .collect(Collectors.toList());

        TestFrameworkSelectOptions testFrameworkOpts = new TestFrameworkSelectOptions(
                testFrameworks,
                new TestFrameworkDTO(TestFramework.DEFAULT_OPTION, messageSource, messageContext)
        );

        List<GormImplDTO> gormImpls = Arrays.stream(GormImpl.values())
                .map(it -> new GormImplDTO(it, messageSource, messageContext))
                .collect(Collectors.toList());

        GormImplSelectOptions gormImplOpts = new GormImplSelectOptions(
                gormImpls,
                new GormImplDTO(GormImpl.DEFAULT_OPTION, messageSource, messageContext)
        );

        List<ServletImplDTO> servletImpls = Arrays.stream(ServletImpl.values())
                .map(it -> new ServletImplDTO(it, messageSource, messageContext))
                .collect(Collectors.toList());

        ServletImplSelectOptions servletImplOpts = new ServletImplSelectOptions(
                servletImpls,
                new ServletImplDTO(ServletImpl.DEFAULT_OPTION, messageSource, messageContext)
        );


        return new SelectOptionsDTO(applicationOpts, jdkVersionOpts, languageOpts, testFrameworkOpts, gormImplOpts, servletImplOpts);

    }
}

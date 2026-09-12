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

import org.springframework.context.MessageSource;
import java.util.Locale;
import org.grails.forge.api.options.*;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.options.DevelopmentReloading;
import org.grails.forge.options.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregator for {@link SelectOptionDTO}.
 *
 * @since 6.0.0
 */
public class SelectOptionsDTO {

    private ApplicationTypeSelectOptions type;

    private JdkVersionSelectOptions jdkVersion;

    private LanguageSelectOptions lang;
    
    private DevelopmentReloadingSelectOptions reloading;

    private GormImplSelectOptions gorm;

    private ServletImplSelectOptions servlet;

    SelectOptionsDTO() {
    }

    public SelectOptionsDTO(ApplicationTypeSelectOptions type,
                            JdkVersionSelectOptions jdkVersion,
                            LanguageSelectOptions lang,
                            DevelopmentReloadingSelectOptions reloading,
                            GormImplSelectOptions gorm,
                            ServletImplSelectOptions servlet) {
        this.type = type;
        this.jdkVersion = jdkVersion;
        this.lang = lang;
        this.reloading = reloading;
        this.gorm = gorm;
        this.servlet = servlet;
    }

        public ApplicationTypeSelectOptions getType() {
        return type;
    }

        public JdkVersionSelectOptions getJdkVersion() {
        return jdkVersion;
    }

        public LanguageSelectOptions getLang() {
        return lang;
    }

        public DevelopmentReloadingSelectOptions getReloading() {
        return reloading;
    }

        public GormImplSelectOptions getGorm() {
        return gorm;
    }

        public ServletImplSelectOptions getServlet() {
        return servlet;
    }

    /**
     * Build the options
     *
     * @param messageSource The message source
     * @param locale The locale
     * @return the supported options
     */
    public static SelectOptionsDTO make(MessageSource messageSource, Locale locale) {

        List<ApplicationTypeDTO> applications = Arrays.stream(ApplicationType.values())
                .map(it -> new ApplicationTypeDTO(it, null, messageSource, locale))
                .collect(Collectors.toList());

        ApplicationTypeSelectOptions applicationOpts = new ApplicationTypeSelectOptions(
                applications,
                new ApplicationTypeDTO(ApplicationType.DEFAULT_OPTION, null, messageSource, locale)
        );

        List<JdkVersionDTO> jdkVersions = Arrays.stream(JdkVersion.values())
                .map(it -> new JdkVersionDTO(it, messageSource, locale))
                .collect(Collectors.toList());

        JdkVersionSelectOptions jdkVersionOpts = new JdkVersionSelectOptions(
                jdkVersions,
                new JdkVersionDTO(JdkVersion.DEFAULT_OPTION, messageSource, locale)
        );

        List<LanguageDTO> languages = Arrays.stream(Language.values())
                .map(it -> new LanguageDTO(it, messageSource, locale))
                .collect(Collectors.toList());

        LanguageSelectOptions languageOpts = new LanguageSelectOptions(
                languages,
                new LanguageDTO(Language.DEFAULT_OPTION, messageSource, locale)
        );

        List<DevelopmentReloadingDTO> developmentReloading = Arrays.stream(DevelopmentReloading.values())
                .map(it -> new DevelopmentReloadingDTO(it, messageSource, locale))
                .collect(Collectors.toList());

        DevelopmentReloadingSelectOptions developmentReloadingOpts = new DevelopmentReloadingSelectOptions(
            developmentReloading,
                new DevelopmentReloadingDTO(DevelopmentReloading.DEFAULT_OPTION, messageSource, locale)
        );

        List<GormImplDTO> gormImpls = Arrays.stream(GormImpl.values())
                .map(it -> new GormImplDTO(it, messageSource, locale))
                .collect(Collectors.toList());

        GormImplSelectOptions gormImplOpts = new GormImplSelectOptions(
                gormImpls,
                new GormImplDTO(GormImpl.DEFAULT_OPTION, messageSource, locale)
        );

        List<ServletImplDTO> servletImpls = Arrays.stream(ServletImpl.values())
                .map(it -> new ServletImplDTO(it, messageSource, locale))
                .collect(Collectors.toList());

        ServletImplSelectOptions servletImplOpts = new ServletImplSelectOptions(
                servletImpls,
                new ServletImplDTO(ServletImpl.DEFAULT_OPTION, messageSource, locale)
        );


        return new SelectOptionsDTO(applicationOpts, jdkVersionOpts, languageOpts, developmentReloadingOpts, gormImplOpts, servletImplOpts);

    }
}

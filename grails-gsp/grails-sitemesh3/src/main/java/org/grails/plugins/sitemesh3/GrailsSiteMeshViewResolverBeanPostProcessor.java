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
package org.grails.plugins.sitemesh3;

import org.sitemesh.webmvc.SiteMeshViewResolverBeanPostProcessor;

/**
 * {@link SiteMeshViewResolverBeanPostProcessor} preconfigured to wrap
 * Grails' {@code gspViewResolver} bean with a
 * {@link GrailsSiteMeshViewResolver}.
 */
public class GrailsSiteMeshViewResolverBeanPostProcessor extends SiteMeshViewResolverBeanPostProcessor {

    /**
     * The primary GSP view resolver bean name in Grails. The historical
     * name {@code jspViewResolver} is kept for compatibility with the
     * plugin's {@code GroovyPagesPostProcessor}, which registers the GSP
     * resolver under that name when it isn't already present (see
     * {@code org.grails.plugins.web.GroovyPagesPostProcessor}). The
     * modern {@code GspAutoConfiguration.gspViewResolver()} bean is
     * aliased to this name too when it fires.
     */
    public static final String TARGET_VIEW_RESOLVER_BEAN_NAME = "jspViewResolver";

    public GrailsSiteMeshViewResolverBeanPostProcessor() {
        setTargetViewResolverBeanName(TARGET_VIEW_RESOLVER_BEAN_NAME);
        setSiteMeshViewResolverClass(GrailsSiteMeshViewResolver.class);
    }
}

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

import java.io.InputStream;
import java.util.Properties;

import asset.pipeline.AssetPipelineConfigHolder;
import asset.pipeline.AssetPipelineFilter;
import asset.pipeline.grails.AssetMethodTagLib;
import asset.pipeline.grails.AssetProcessorService;
import asset.pipeline.grails.AssetsTagLib;

import grails.core.GrailsApplication;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Uses a tag library written for Grails - the asset pipeline's {@code <asset:...>} - from a Spring
 * Boot application.
 *
 * <p>A Grails application finds such a tag library by scanning the artefacts of the plugin that
 * carries it. This application has no plugins, so it declares the tag library as a bean; GSP's tag
 * library lookup registers every tag library bean of the context, and the tags are then usable in a
 * page exactly as the ones GSP contributes itself.
 *
 * <p>The service the tag library reads asset URLs from is auto-configured by the asset pipeline, so
 * only the tag library and the filter that serves the compiled assets are declared here.
 *
 * <p>Wiring this by hand is what an application does today, and it belongs in the asset pipeline
 * rather than in every application that wants it: the same beans are being contributed there as an
 * auto-configuration, and this class goes when a release carries it.
 */
@Configuration(proxyBeanMethods = false)
public class AssetPipelineConfiguration {

    /**
     * The {@code <asset:...>} tag library. What it takes is given to it here rather than by name, as
     * a Grails application would: the service it asks for asset URLs, and the application it reads
     * its configuration from.
     */
    @Bean
    public AssetsTagLib assetsTagLib(AssetProcessorService assetProcessorService, GrailsApplication grailsApplication) {
        AssetsTagLib assetsTagLib = new AssetsTagLib();
        assetsTagLib.setAssetProcessorService(assetProcessorService);
        assetsTagLib.setGrailsApplication(grailsApplication);
        return assetsTagLib;
    }

    /**
     * The second tag library the asset pipeline carries, which puts {@code assetPath} in the default
     * namespace. The one above builds every URL by calling it, so both are needed - as a Grails
     * application gets both, being artefacts of the same plugin.
     */
    @Bean
    public AssetMethodTagLib assetMethodTagLib(AssetProcessorService assetProcessorService) {
        AssetMethodTagLib assetMethodTagLib = new AssetMethodTagLib();
        assetMethodTagLib.setAssetProcessorService(assetProcessorService);
        return assetMethodTagLib;
    }

    /**
     * Hands the pipeline the manifest its build wrote, which maps each asset to the digest-named
     * file compiled from it. A Grails application has this done by the plugin; here it is read from
     * the class path the artifact packages it on.
     */
    @Bean
    public InitializingBean assetManifest(ResourceLoader resourceLoader) {
        return () -> {
            Resource manifest = resourceLoader.getResource("classpath:assets/manifest.properties");
            if (manifest.exists()) {
                Properties properties = new Properties();
                try (InputStream input = manifest.getInputStream()) {
                    properties.load(input);
                }
                AssetPipelineConfigHolder.manifest = properties;
            }
        };
    }

    /**
     * Serves what the pipeline compiled, from {@code build/assets} while the application runs from
     * the project and from the artifact once it is packaged.
     */
    @Bean
    public FilterRegistrationBean<AssetPipelineFilter> assetPipelineFilter() {
        FilterRegistrationBean<AssetPipelineFilter> registration =
                new FilterRegistrationBean<>(new AssetPipelineFilter());
        registration.addUrlPatterns("/assets/*");
        return registration;
    }
}

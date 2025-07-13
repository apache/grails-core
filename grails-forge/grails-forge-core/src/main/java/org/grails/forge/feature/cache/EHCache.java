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
package org.grails.forge.feature.cache;

import jakarta.inject.Singleton;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;

import java.util.Map;

@Singleton
public class EHCache implements CacheFeature {

    @Override
    public String getName() {
        return "cache-ehcache";
    }

    @Override
    public String getTitle() {
        return "Grails EHCache Plugin";
    }

    @Override
    public String getDescription() {
        return "The Grails Cache Ehcache plugin extends the Cache plugin and uses Ehcache as the storage provider for cached content.";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        Map<String, Object> config  = generatorContext.getConfiguration();
        config.put("grails.cache.ehcache.ehcacheXmlLocation", "classpath:ehcache.xml");
        config.put("grails.cache.ehcache.lockTimeout", 200);
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.grails.plugins")
                .lookupArtifactId("cache-ehcache")
                .implementation());
    }

    @Override
    public String getThirdPartyDocumentation() {
        return "https://www.ehcache.org/";
    }

    @Override
    public String getDocumentation() {
        return "https://grails-plugins.github.io/grails-cache-ehcache/latest/";
    }

}

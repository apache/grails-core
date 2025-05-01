/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.feature.grails;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.options.Options;
import org.grails.forge.template.URLTemplate;

import java.util.Arrays;
import java.util.Set;

@Singleton
public class GrailsDefaultPlugins implements DefaultFeature {
    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return true;
    }

    @Override
    public String getName() {
        return "grails-dependencies";
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        Arrays.asList("rest-transforms", "databinding", "i18n", "services", "url-mappings", "interceptors")
                .forEach((artifact) -> {
                    generatorContext.addDependency(Dependency.builder()
                            .groupId("org.apache.grails")
                            .artifactId("grails-" + artifact)
                            .implementation());
                });
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        generatorContext.addTemplate("messages_properties", new URLTemplate("grails-app/i18n/messages.properties", classLoader.getResource("i18n/messages.properties"), false));
        generatorContext.addTemplate("messages_cs_properties", new URLTemplate("grails-app/i18n/messages_cs.properties", classLoader.getResource("i18n/messages_cs.properties")));
        generatorContext.addTemplate("messages_da_properties", new URLTemplate("grails-app/i18n/messages_da.properties", classLoader.getResource("i18n/messages_da.properties")));
        generatorContext.addTemplate("messages_de_properties", new URLTemplate("grails-app/i18n/messages_de.properties", classLoader.getResource("i18n/messages_de.properties")));
        generatorContext.addTemplate("messages_es_properties", new URLTemplate("grails-app/i18n/messages_es.properties", classLoader.getResource("i18n/messages_es.properties")));
        generatorContext.addTemplate("messages_fr_properties", new URLTemplate("grails-app/i18n/messages_fr.properties", classLoader.getResource("i18n/messages_fr.properties")));
        generatorContext.addTemplate("messages_it_properties", new URLTemplate("grails-app/i18n/messages_it.properties", classLoader.getResource("i18n/messages_it.properties")));
        generatorContext.addTemplate("messages_ja_properties", new URLTemplate("grails-app/i18n/messages_ja.properties", classLoader.getResource("i18n/messages_ja.properties")));
        generatorContext.addTemplate("messages_nb_properties", new URLTemplate("grails-app/i18n/messages_nb.properties", classLoader.getResource("i18n/messages_nb.properties")));
        generatorContext.addTemplate("messages_nl_properties", new URLTemplate("grails-app/i18n/messages_nl.properties", classLoader.getResource("i18n/messages_nl.properties")));
        generatorContext.addTemplate("messages_pl_properties", new URLTemplate("grails-app/i18n/messages_pl.properties", classLoader.getResource("i18n/messages_pl.properties")));
        generatorContext.addTemplate("messages_pt_BR_properties", new URLTemplate("grails-app/i18n/messages_pt_BR.properties", classLoader.getResource("i18n/messages_pt_BR.properties")));
        generatorContext.addTemplate("messages_pt_PT_properties", new URLTemplate("grails-app/i18n/messages_pt_PT.properties", classLoader.getResource("i18n/messages_pt_PT.properties")));
        generatorContext.addTemplate("messages_ru_properties", new URLTemplate("grails-app/i18n/messages_ru.properties", classLoader.getResource("i18n/messages_ru.properties")));
        generatorContext.addTemplate("messages_sk_properties", new URLTemplate("grails-app/i18n/messages_sk.properties", classLoader.getResource("i18n/messages_sk.properties")));
        generatorContext.addTemplate("messages_sv_properties", new URLTemplate("grails-app/i18n/messages_sv.properties", classLoader.getResource("i18n/messages_sv.properties")));
        generatorContext.addTemplate("messages_th_properties", new URLTemplate("grails-app/i18n/messages_th.properties", classLoader.getResource("i18n/messages_th.properties")));
        generatorContext.addTemplate("messages_zh_CN_properties", new URLTemplate("grails-app/i18n/messages_zh_CN.properties", classLoader.getResource("i18n/messages_zh_CN.properties")));
    }

    @Override
    public String getCategory() {
        return Category.SERVER;
    }
}

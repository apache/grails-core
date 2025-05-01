/*
 * Copyright 2017-2024 original authors
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
package org.grails.forge.feature.database;

import io.micronaut.context.annotation.Primary;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.Options;

import java.util.Map;
import java.util.Set;

@Primary
@Singleton
public class HibernateGorm extends GormFeature implements DatabaseDriverConfigurationFeature {

    static final String PREFIX = "dataSource.";
    private static final String URL_KEY = PREFIX + "url";
    private static final String DRIVER_KEY = PREFIX + "driverClassName";
    private static final String USERNAME_KEY = PREFIX + "username";
    private static final String PASSWORD_KEY = PREFIX + "password";
    private static final String DB_CREATE_KEY = PREFIX + "dbCreate";

    private final DatabaseDriverFeature defaultDbFeature;

    public HibernateGorm(DatabaseDriverFeature defaultDbFeature) {
        this.defaultDbFeature = defaultDbFeature;
    }

    @Override
    public String getName() {
        return "gorm-hibernate5";
    }

    @Override
    public String getTitle() {
        return "GORM for Hibernate5";
    }

    @Override
    public String getDescription() {
        return "Adds support for Hibernate5 using GORM";
    }

    @Override
    public void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(DatabaseDriverFeature.class)) {
            featureContext.addFeature(defaultDbFeature);
        }
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        Map<String, Object> config = generatorContext.getConfiguration();
        DatabaseDriverFeature dbFeature = generatorContext.getRequiredFeature(DatabaseDriverFeature.class);
        applyDefaultConfig(dbFeature, config);
        applyDefaultGormConfig(config);
        config.put("dataSource.pooled", true);
        config.put("dataSource.jmxExport", true);
        config.put("hibernate.cache.queries", false);
        config.put("hibernate.cache.use_second_level_cache", false);
        config.put("hibernate.cache.use_query_cache", false);

        generatorContext.addBuildscriptDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-data-hibernate5")
                .buildSrc());
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-data-hibernate5")
                .implementation());
        generatorContext.addDependency(Dependency.builder()
                .groupId("com.zaxxer")
                .artifactId("HikariCP")
                .runtimeOnly());
    }

    @Override
    public String getUrlKey() {
        return URL_KEY;
    }

    @Override
    public String getDriverKey() {
        return DRIVER_KEY;
    }

    @Override
    public String getUsernameKey() {
        return USERNAME_KEY;
    }

    @Override
    public String getPasswordKey() {
        return PASSWORD_KEY;
    }

    @Override
    public String getDbCreateKey() {
        return DB_CREATE_KEY;
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return selectedFeatures.stream().anyMatch(f -> f instanceof HibernateGorm) || options.getGormImpl() == GormImpl.HIBERNATE;
    }
}

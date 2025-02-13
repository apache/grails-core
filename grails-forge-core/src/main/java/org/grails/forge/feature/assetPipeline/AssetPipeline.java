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
package org.grails.forge.feature.assetPipeline;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.CoordinateResolver;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.build.gradle.GradlePlugin;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.assetPipeline.templates.assetPipelineExtension;
import org.grails.forge.options.Options;
import org.grails.forge.template.RockerWritable;
import org.grails.forge.template.URLTemplate;
import org.grails.forge.util.VersionInfo;

import java.util.Set;

@Singleton
public class AssetPipeline implements DefaultFeature {

    private final CoordinateResolver coordinateResolver;

    public AssetPipeline(CoordinateResolver coordinateResolver) {
        this.coordinateResolver = coordinateResolver;
    }

    @NonNull
    @Override
    public String getName() {
        return "asset-pipeline-grails";
    }

    @Override
    public String getTitle() {
        return "Asset Pipeline Core";
    }

    @NonNull
    @Override
    public String getDescription() {
        return "The Asset-Pipeline is a plugin used for managing and processing static assets in JVM applications primarily via Gradle (however not mandatory). Read more at https://github.com/bertramdev/asset-pipeline";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addBuildPlugin(GradlePlugin.builder()
                .id("com.bertramlabs.asset-pipeline")
                .extension(new RockerWritable(assetPipelineExtension.template(generatorContext.getApplicationType())))
                .version("$assetPipelineGradleVersion")
                .build());

        generatorContext.getBuildProperties().put("assetPipelineGradleVersion", VersionInfo.getBomVersion("asset-pipeline-gradle"));

        generatorContext.addDependency(Dependency.builder()
                .groupId("com.bertramlabs.plugins")
                .artifactId("asset-pipeline-grails")
                .runtimeOnly());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.webjars.npm")
                .artifactId("bootstrap")
                .developmentOnly());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.webjars.npm")
                .artifactId("bootstrap-icons")
                .developmentOnly());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.webjars.npm")
                .artifactId("jquery")
                .developmentOnly());

        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        generatorContext.addTemplate("advancedgrails_svg", new URLTemplate("grails-app/assets/images/advancedgrails.svg", classLoader.getResource("assets/images/advancedgrails.svg")));
        generatorContext.addTemplate("apple-touch-icon_png", new URLTemplate("grails-app/assets/images/apple-touch-icon.png", classLoader.getResource("assets/images/apple-touch-icon.png")));
        generatorContext.addTemplate("apple-touch-icon-retina_png", new URLTemplate("grails-app/assets/images/apple-touch-icon-retina.png", classLoader.getResource("assets/images/apple-touch-icon-retina.png")));
        generatorContext.addTemplate("documentation_svg", new URLTemplate("grails-app/assets/images/documentation.svg", classLoader.getResource("assets/images/documentation.svg")));
        generatorContext.addTemplate("favicon_ico", new URLTemplate("grails-app/assets/images/favicon.ico", classLoader.getResource("assets/images/favicon.ico")));
        generatorContext.addTemplate("grails_svg", new URLTemplate("grails-app/assets/images/grails.svg", classLoader.getResource("assets/images/grails.svg")));
        generatorContext.addTemplate("grails-cupsonly-logo-white_svg", new URLTemplate("grails-app/assets/images/grails-cupsonly-logo-white.svg", classLoader.getResource("assets/images/grails-cupsonly-logo-white.svg")));
        generatorContext.addTemplate("slack_svg", new URLTemplate("grails-app/assets/images/slack.svg", classLoader.getResource("assets/images/slack.svg")));

        generatorContext.addTemplate("application_js", new URLTemplate("grails-app/assets/javascripts/application.js", classLoader.getResource("assets/javascripts/application.js")));

        generatorContext.addTemplate("application_css", new URLTemplate("grails-app/assets/stylesheets/application.css", classLoader.getResource("assets/stylesheets/application.css")));
        generatorContext.addTemplate("errors_css", new URLTemplate("grails-app/assets/stylesheets/errors.css", classLoader.getResource("assets/stylesheets/errors.css")));
        generatorContext.addTemplate("grails_css", new URLTemplate("grails-app/assets/stylesheets/grails.css", classLoader.getResource("assets/stylesheets/grails.css")));
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.WEB_PLUGIN;
    }

    @Override
    public String getCategory() {
        return Category.VIEW;
    }

    @Override
    public String getDocumentation() {
        return "https://github.com/bertramdev/asset-pipeline#readme";

        // The site is currently offline (2024-11-28), replace the above when online again
        // return "https://www.asset-pipeline.com/manual/";
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return applicationType != ApplicationType.REST_API && applicationType != ApplicationType.PLUGIN;
    }
}

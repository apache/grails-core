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

package grails.gsp.boot;

import java.beans.Introspector;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ViewResolver;

import grails.core.ApplicationAttributes;
import grails.core.GrailsApplication;
import org.grails.encoder.CodecLookup;
import org.grails.encoder.impl.StandaloneCodecLookup;
import org.grails.gsp.GroovyPagesTemplateEngine;
import org.grails.gsp.io.GroovyPageScriptSource;
import org.grails.gsp.jsp.TagLibraryResolver;
import org.grails.gsp.jsp.TagLibraryResolverImpl;
import org.grails.plugins.sitemesh3.Sitemesh3EnvironmentPostProcessor;
import org.grails.plugins.sitemesh3.Sitemesh3LayoutTagLib;
import org.grails.plugins.web.taglib.RenderSitemeshTagLib;
import org.grails.plugins.web.taglib.RenderTagLib;
import org.grails.taglib.TagLibraryLookup;
import org.grails.web.gsp.GroovyPagesTemplateRenderer;
import org.grails.web.gsp.io.CachingGrailsConventionGroovyPageLocator;
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator;
import org.grails.web.pages.StandaloneTagLibraryLookup;
import org.grails.web.servlet.view.GroovyPageViewResolver;

@Configuration
// The codec lookup below stands in for the one the codecs module contributes, which is why this is
// configured after it: both bean definitions are named codecLookup, and without an order between
// them the unconditional one overrode the conditional one, which is a bean definition override an
// application had to allow before it would start.
// The codecs module is named rather than referenced so that GSP keeps working for an application
// that does not have it on the class path.
@AutoConfigureAfter(value = WebMvcAutoConfiguration.class, name = "org.grails.plugins.codecs.CodecsConfiguration")
public class GspAutoConfiguration {
    protected static abstract class AbstractGspConfig {
        @Value("${spring.gsp.reloadingEnabled:true}")
        boolean gspReloadingEnabled;

        @Value("${spring.gsp.view.cacheTimeout:1000}")
        long viewCacheTimeout;

        @Value("${spring.gsp.jspEnabled:true}")
        boolean jspEnabled;

        @Value("${spring.gsp.sitmesh3:true}")
        boolean sitemesh3;
    }

    @Configuration
    @Import({TagLibraryLookupRegistrar.class, ReplaceViewResolverRegistrar.class})
    protected static class GspTemplateEngineAutoConfiguration extends AbstractGspConfig {
        /** Where the {@code compileGroovyPages} build task writes the views it compiled. */
        protected static final String PRECOMPILED_VIEWS_LOCATION = "classpath:gsp/views.properties";

        private static final String LOCAL_DIRECTORY_TEMPLATE_ROOT = "./src/main/resources/templates";
        private static final String CLASSPATH_TEMPLATE_ROOT = "classpath:/templates";

        private static final Logger logger = LoggerFactory.getLogger(GspTemplateEngineAutoConfiguration.class);

        @Value("${spring.gsp.templateRoots:}")
        String[] templateRoots;

        @Value("${spring.gsp.locator.cacheTimeout:5000}")
        long locatorCacheTimeout;

        @Value("${spring.gsp.layout.caching:true}")
        boolean gspLayoutCaching;

        @Value("${sitemesh.decorator.default:}")
        String defaultLayoutName;

        @Bean
        @ConditionalOnMissingBean(name = "groovyPagesTemplateEngine")
        GroovyPagesTemplateEngine groovyPagesTemplateEngine(ObjectProvider<TagLibraryResolver> tagLibraryResolver, TagLibraryLookup tagLibraryLookup, GroovyPagesTemplateRenderer groovyPagesTemplateRenderer) {
            GroovyPagesTemplateEngine templateEngine = new GroovyPagesTemplateEngine();
            templateEngine.setReloadEnabled(gspReloadingEnabled);
            // A resolver is contributed only where JSP support is on the class path. Without it a page
            // can use no JSP tag library, which it could not do anyway, and renders GSP as it always did.
            templateEngine.setJspTagLibraryResolver(tagLibraryResolver.getIfAvailable());
            templateEngine.setTagLibraryLookup(tagLibraryLookup);
            groovyPagesTemplateRenderer.setGroovyPagesTemplateEngine(templateEngine);
            return templateEngine;
        }

        @Bean
        @ConditionalOnMissingBean(name = "groovyPageLocator")
        GrailsConventionGroovyPageLocator groovyPageLocator(ResourceLoader resourceLoader) {
            final List<String> templateRootsCleaned = resolveTemplateRoots();
            CachingGrailsConventionGroovyPageLocator pageLocator = new CachingGrailsConventionGroovyPageLocator() {
                protected List<String> resolveSearchPaths(String uri) {
                    List<String> paths = new ArrayList<>(templateRootsCleaned.size() + 1);
                    for (String rootPath : templateRootsCleaned) {
                        paths.add(rootPath + cleanUri(uri));
                    }
                    // the path a precompiled view is registered under, which names no resource root
                    paths.add(cleanUri(uri));
                    return paths;
                }

                protected String cleanUri(String uri) {
                    uri = StringUtils.cleanPath(uri);
                    if (!uri.startsWith("/")) {
                        uri = "/" + uri;
                    }
                    return uri;
                }

                public GroovyPageScriptSource findViewByPath(String uri) {
                    return super.findViewByPath(cleanUri(uri));
                }
            };
            pageLocator.setReloadEnabled(gspReloadingEnabled);
            pageLocator.setCacheTimeout(gspReloadingEnabled ? locatorCacheTimeout : -1);
            Map<String, String> precompiledViews = resolvePrecompiledViews(resourceLoader, templateRootsCleaned);
            if (precompiledViews != null) {
                pageLocator.setPrecompiledGspMap(precompiledViews);
            }
            return pageLocator;
        }

        /**
         * The registry of views compiled by the {@code compileGroovyPages} build task, read from
         * {@value #PRECOMPILED_VIEWS_LOCATION}, or {@code null} when there is none to read.
         *
         * <p>A template root on the file system means the templates are there to be rendered, and
         * re-rendered as they are edited, so the registry is left unread for it: the locator prefers a
         * precompiled view over a template it can find, and would otherwise serve the view as it stood
         * when the application was built.
         */
        protected Map<String, String> resolvePrecompiledViews(ResourceLoader resourceLoader, List<String> templateRoots) {
            for (String templateRoot : templateRoots) {
                if (templateRoot.startsWith(ResourceUtils.FILE_URL_PREFIX)) {
                    return null;
                }
            }
            Resource resource = resourceLoader.getResource(PRECOMPILED_VIEWS_LOCATION);
            if (!resource.exists()) {
                return null;
            }
            Properties views = new Properties();
            try (InputStream input = resource.getInputStream()) {
                views.load(input);
            } catch (IOException e) {
                logger.warn("Unable to read the precompiled view registry [{}]; views will be rendered from " +
                        "their templates, if those are available", PRECOMPILED_VIEWS_LOCATION, e);
                return null;
            }
            Map<String, String> precompiledViews = new LinkedHashMap<>(views.size());
            for (String uri : views.stringPropertyNames()) {
                precompiledViews.put(uri, views.getProperty(uri));
            }
            return precompiledViews;
        }

        protected List<String> resolveTemplateRoots() {
            if (templateRoots.length > 0) {
                List<String> rootPaths = new ArrayList<>(templateRoots.length);
                for (String rootPath : templateRoots) {
                    rootPath = rootPath.trim();
                    // remove trailing slash since uri will always be prefixed with a slash
                    if (rootPath.endsWith("/")) {
                        rootPath = rootPath.substring(0, rootPath.length() - 1);
                    }
                    if (StringUtils.hasLength(rootPath)) {
                        rootPaths.add(rootPath);
                    }
                }
                return rootPaths;
            } else {
                if (gspReloadingEnabled) {
                    File templateRootDirectory = new File(LOCAL_DIRECTORY_TEMPLATE_ROOT);
                    if (templateRootDirectory.isDirectory()) {
                        return Collections.singletonList("file:" + LOCAL_DIRECTORY_TEMPLATE_ROOT);
                    }
                }
                return Collections.singletonList(CLASSPATH_TEMPLATE_ROOT);
            }
        }

        @Bean
        @ConditionalOnMissingBean(name = "groovyPagesTemplateRenderer")
        GroovyPagesTemplateRenderer groovyPagesTemplateRenderer(GrailsConventionGroovyPageLocator groovyPageLocator) {
            GroovyPagesTemplateRenderer groovyPagesTemplateRenderer = new GroovyPagesTemplateRenderer();
            groovyPagesTemplateRenderer.setCacheEnabled(!gspReloadingEnabled);
            groovyPagesTemplateRenderer.setGroovyPageLocator(groovyPageLocator);
            return groovyPagesTemplateRenderer;
        }
    }

    /**
     * Applies the SiteMesh defaults to the environment of a context that was not built by
     * {@code SpringApplication}, where {@link Sitemesh3EnvironmentPostProcessor} - which applies them
     * to every application that was - does not run.
     *
     * <p>It is a {@link BeanFactoryPostProcessor} so that it is instantiated, and so applies the
     * defaults through {@link EnvironmentAware}, before anything reads a SiteMesh property; the
     * post-processing itself has nothing to do. It declares no beans, so it is not proxied - which
     * also settles the warning that being created this early otherwise draws.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(name = "sitemesh3")
    protected static class Sitemesh3Configuration implements EnvironmentAware, BeanFactoryPostProcessor {
        @Override
        public void setEnvironment(Environment environment) {
            if (environment instanceof ConfigurableEnvironment) {
                ConfigurableEnvironment configEnv = (ConfigurableEnvironment) environment;
                MapPropertySource defaults = Sitemesh3EnvironmentPostProcessor.getDefaultPropertySource(configEnv);
                if (!defaults.getSource().isEmpty()) {
                    configEnv.getPropertySources().addFirst(defaults);
                }
            }
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {}
    }

    @Configuration
    protected static class GspViewResolverConfiguration extends AbstractGspConfig {
        /**
         * Also answers to {@code jspViewResolver}, the name Grails gives an application's own view
         * resolver: {@code RenderSitemeshTagLib} resolves the layout view of {@code <g:applyLayout>}
         * through a resolver qualified by that name, and a standalone Spring Boot application has no
         * bean of its own under it.
         */
        @Bean(name = { "gspViewResolver", "jspViewResolver" })
        @ConditionalOnMissingBean(name = "gspViewResolver")
        public ViewResolver gspViewResolver(GroovyPagesTemplateEngine groovyPagesTemplateEngine, GrailsConventionGroovyPageLocator groovyPageLocator) {
            GroovyPageViewResolver groovyPageViewResolver = new GroovyPageViewResolver(groovyPagesTemplateEngine, groovyPageLocator);
            groovyPageViewResolver.setResolveJspView(jspEnabled);
            groovyPageViewResolver.setAllowGrailsViewCaching(!gspReloadingEnabled || viewCacheTimeout != 0);
            groovyPageViewResolver.setCacheTimeout(gspReloadingEnabled ? viewCacheTimeout : -1);
            return groovyPageViewResolver;
        }
    }

    @Configuration
    protected static class CodecLookupConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "codecLookup")
        public CodecLookup codecLookup() {
            return new StandaloneCodecLookup();
        }
    }

    @Configuration
    protected static class StandaloneGrailsApplicationConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "grailsApplication")
        public GrailsApplication grailsApplication() {
            return new StandaloneGrailsApplication();
        }

        /**
         * Hands that application to the GSP beans that expect it, which a Grails application has
         * from its core plugin and an application rendering views with GSP has from here.
         */
        @Bean
        @ConditionalOnMissingBean(name = "grailsApplicationAwarePostProcessor")
        static BeanPostProcessor grailsApplicationAwarePostProcessor(ObjectProvider<GrailsApplication> grailsApplication) {
            return new StandaloneGrailsApplicationAwareBeanPostProcessor(grailsApplication);
        }
    }

    protected static class TagLibraryLookupRegistrar implements ImportBeanDefinitionRegistrar {

        // Sitemesh3LayoutTagLib carries the grailsLayout namespace the GSP compiler's layout
        // preprocessor emits for the <head>, <title> and <body> of every decorated page; without it
        // those capture tags reach the browser as literal markup and nothing is decorated.
        public static final Class<?>[] DEFAULT_TAGLIB_CLASSES = new Class<?>[] {
            RenderTagLib.class, RenderSitemeshTagLib.class, Sitemesh3LayoutTagLib.class };

        /**
         * Registers the tag libraries as beans of the context and the lookup that finds them, which
         * it does once they all exist rather than by holding them. Every tag library takes the
         * lookup itself - {@code TagLibraryInvoker} autowires it - so a lookup that held its tag
         * libraries would be a bean each of them depended on and which depended on each of them,
         * a cycle an application had to allow before it would start.
         */
        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            registerTagLibs(registry);
            if (!registry.containsBeanDefinition("gspTagLibraryLookup")) {
                registry.registerBeanDefinition("gspTagLibraryLookup",
                        createBeanDefinition(StandaloneTagLibraryLookup.class));
                registry.registerAlias("gspTagLibraryLookup", "tagLibraryLookup");
            }
        }

        protected void registerTagLibs(BeanDefinitionRegistry registry) {
            for (Class<?> taglibClazz : DEFAULT_TAGLIB_CLASSES) {
                String beanName = Introspector.decapitalize(taglibClazz.getSimpleName());
                if (!registry.containsBeanDefinition(beanName)) {
                    registry.registerBeanDefinition(beanName, createTagLibBeanDefinition(taglibClazz));
                }
            }
        }

        protected GenericBeanDefinition createBeanDefinition(Class<?> beanClass) {
            GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
            beanDefinition.setBeanClass(beanClass);
            beanDefinition.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_NAME);
            return beanDefinition;
        }

        /**
         * Tag libraries are wired from their own {@code @Autowired} declarations rather than by name.
         * A tag library depends on the view resolver, which depends on the template engine, which
         * depends on the lookup; they break that cycle by declaring the dependency {@code @Lazy}.
         * Autowiring them by name would inject the view resolver eagerly - {@code viewResolver} is an
         * alias of {@code gspViewResolver} - and the context would fail to start with a cycle nothing
         * could break.
         */
        protected GenericBeanDefinition createTagLibBeanDefinition(Class<?> beanClass) {
            GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
            beanDefinition.setBeanClass(beanClass);
            return beanDefinition;
        }
    }

    /**
     * Makes the GSP view resolver the primary {@code viewResolver} for GSP applications by
     * replacing the {@code viewResolver} bean ({@link WebMvcAutoConfiguration}'s
     * {@code ContentNegotiatingViewResolver}) with an alias to {@code gspViewResolver}.
     *
     * <p>Removal of {@link WebMvcAutoConfiguration}'s {@code defaultViewResolver}
     * ({@code InternalResourceViewResolver}) is handled centrally for every Grails servlet web
     * application by {@code GrailsViewResolverAutoConfiguration} (grails-controllers), so it is
     * not repeated here.
     *
     * <p>Controlled by the {@code spring.gsp.replaceViewResolverBean} configuration property.
     */
    protected static class ReplaceViewResolverRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {
        boolean replaceViewResolverBean;

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            if (replaceViewResolverBean) {
                if (registry.containsBeanDefinition("viewResolver")) {
                    registry.removeBeanDefinition("viewResolver");
                }
                registry.registerAlias("gspViewResolver", "viewResolver");
            }
        }

        @Override
        public void setEnvironment(Environment environment) {
            replaceViewResolverBean = environment.getProperty("spring.gsp.replaceViewResolverBean", Boolean.class, true);
        }
    }

    @ConditionalOnClass({TagLibraryResolverImpl.class})
    @Configuration
    protected static class GspJspIntegrationConfiguration implements EnvironmentAware {
        @Bean
        public TagLibraryResolverImpl jspTagLibraryResolver() {
            return new TagLibraryResolverImpl();
        }

        @Override
        public void setEnvironment(Environment environment) {
            if (environment instanceof ConfigurableEnvironment) {
                ConfigurableEnvironment configEnv = (ConfigurableEnvironment) environment;
                Properties defaultProperties = createDefaultProperties();
                configEnv.getPropertySources().addLast(new PropertiesPropertySource(GspJspIntegrationConfiguration.class.getName(), defaultProperties));
            }
        }

        protected Properties createDefaultProperties() {
            Properties defaultProperties = new Properties();
            // scan for spring JSP taglib tld files by default, also scan for
            defaultProperties.put("grails.gsp.tldScanPattern", "classpath*:/META-INF/spring*.tld,classpath*:/META-INF/fmt.tld,classpath*:/META-INF/c.tld,classpath*:/META-INF/c-1_0-rt.tld");
            return defaultProperties;
        }
    }

    @Configuration
    protected static class ServletContextConfiguration implements ServletContextAware {

        @Autowired GrailsApplication grailsApplication;
        @Autowired WebApplicationContext webContext;

        // mimics GrailsConfigUtils.configureServletContextAttributes()
        @Override
        public void setServletContext(ServletContext servletContext) {
            // use config file locations if available
            servletContext.setAttribute(ApplicationAttributes.PARENT_APPLICATION_CONTEXT, webContext.getParent());
            servletContext.setAttribute(GrailsApplication.APPLICATION_ID, grailsApplication);

            servletContext.setAttribute(ApplicationAttributes.APPLICATION_CONTEXT, webContext);
            servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webContext);
        }
    }
}

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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import grails.core.ApplicationAttributes;
import grails.core.GrailsApplication;
import org.grails.encoder.impl.StandaloneCodecLookup;
import org.grails.encoder.CodecLookup;
import org.grails.gsp.GroovyPagesTemplateEngine;
import org.grails.gsp.io.GroovyPageScriptSource;
import org.grails.gsp.jsp.TagLibraryResolver;
import org.grails.plugins.web.taglib.RenderGrailsLayoutTagLib;
import org.grails.taglib.TagLibraryLookup;
import org.grails.web.gsp.GroovyPagesTemplateRenderer;
import org.grails.web.pages.StandaloneTagLibraryLookup;
import org.grails.web.gsp.io.CachingGrailsConventionGroovyPageLocator;
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator;
import org.grails.gsp.jsp.TagLibraryResolverImpl;
import org.grails.web.servlet.view.EmbeddedGrailsLayoutViewResolver;
import org.grails.web.servlet.view.GroovyPageViewResolver;
import org.apache.grails.web.layout.GroovyPageLayoutFinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.*;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ViewResolver;

import org.grails.plugins.web.taglib.GrailsLayoutTagLib;
import org.grails.plugins.web.taglib.RenderTagLib;

import jakarta.servlet.ServletContext;

@Configuration
@AutoConfigureAfter(WebMvcAutoConfiguration.class)
public class GspAutoConfiguration {
    protected static abstract class AbstractGspConfig {
        @Value("${spring.gsp.reloadingEnabled:true}")
        boolean gspReloadingEnabled;
        
        @Value("${spring.gsp.view.cacheTimeout:1000}")
        long viewCacheTimeout;

        @Value("${spring.gsp.jspEnabled:true}")
        boolean jspEnabled;
    }

    @Configuration
    @Import({TagLibraryLookupRegistrar.class, RemoveDefaultViewResolverRegistrar.class})
    protected static class GspTemplateEngineAutoConfiguration extends AbstractGspConfig {
        private static final String LOCAL_DIRECTORY_TEMPLATE_ROOT="./src/main/resources/templates";
        private static final String CLASSPATH_TEMPLATE_ROOT="classpath:/templates";
        
        @Value("${spring.gsp.templateRoots:}")
        String[] templateRoots;
        
        @Value("${spring.gsp.locator.cacheTimeout:5000}")
        long locatorCacheTimeout;
        
        @Value("${spring.gsp.layout.caching:true}")
        boolean gspLayoutCaching;

        @Value("${spring.gsp.layout.default:main}")
        String defaultLayoutName;

        @Bean
        @ConditionalOnMissingBean(name="groovyPagesTemplateEngine") 
        GroovyPagesTemplateEngine groovyPagesTemplateEngine(TagLibraryResolver tagLibraryResolver, TagLibraryLookup tagLibraryLookup, GroovyPagesTemplateRenderer groovyPagesTemplateRenderer) {
            GroovyPagesTemplateEngine templateEngine = new GroovyPagesTemplateEngine();
            templateEngine.setReloadEnabled(gspReloadingEnabled);
            templateEngine.setJspTagLibraryResolver(tagLibraryResolver);
            templateEngine.setTagLibraryLookup(tagLibraryLookup);
            groovyPagesTemplateRenderer.setGroovyPagesTemplateEngine(templateEngine);
            return templateEngine;
        }
        
        @Bean
        @ConditionalOnMissingBean(name="groovyPageLocator")
        GrailsConventionGroovyPageLocator groovyPageLocator() {
            final List<String> templateRootsCleaned=resolveTemplateRoots();
            CachingGrailsConventionGroovyPageLocator pageLocator = new CachingGrailsConventionGroovyPageLocator() {
                protected List<String> resolveSearchPaths(String uri) {
                    List<String> paths=new ArrayList<String>(templateRootsCleaned.size());
                    for (String rootPath : templateRootsCleaned) {
                        paths.add(rootPath + cleanUri(uri));
                    }
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
            return pageLocator;
        }

        protected List<String> resolveTemplateRoots() {
            if (templateRoots.length > 0) {
                List<String> rootPaths = new ArrayList<String>(templateRoots.length);
                for (String rootPath : templateRoots) {
                    rootPath = rootPath.trim();
                    // remove trailing slash since uri will always be prefixed with a slash
                    if (rootPath.endsWith("/")) {
                        rootPath = rootPath.substring(0, rootPath.length()-1);
                    }
                    if (!StringUtils.isEmpty(rootPath)) {
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
        @ConditionalOnMissingBean(name = "groovyPageLayoutFinder")
        public GroovyPageLayoutFinder groovyPageLayoutFinder() {
            GroovyPageLayoutFinder groovyPageLayoutFinder = new GroovyPageLayoutFinder();
            groovyPageLayoutFinder.setGspReloadEnabled(gspReloadingEnabled);
            groovyPageLayoutFinder.setCacheEnabled(gspLayoutCaching);
            groovyPageLayoutFinder.setEnableNonGspViews(false);
            groovyPageLayoutFinder.setDefaultDecoratorName(defaultLayoutName);
            return groovyPageLayoutFinder;
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

    @Configuration
    protected static class GspViewResolverConfiguration extends AbstractGspConfig {
        @Bean
        @ConditionalOnMissingBean(name = "gspViewResolver")
        public ViewResolver gspViewResolver(GroovyPagesTemplateEngine groovyPagesTemplateEngine, GrailsConventionGroovyPageLocator groovyPageLocator, GroovyPageLayoutFinder groovyPageLayoutFinder) {
            GroovyPageViewResolver groovyPageViewResolver = new GroovyPageViewResolver(groovyPagesTemplateEngine, groovyPageLocator);
            groovyPageViewResolver.setResolveJspView(jspEnabled);
            groovyPageViewResolver.setAllowGrailsViewCaching(!gspReloadingEnabled || viewCacheTimeout != 0);
            groovyPageViewResolver.setCacheTimeout(gspReloadingEnabled ? viewCacheTimeout : -1);
            return new EmbeddedGrailsLayoutViewResolver(groovyPageViewResolver, groovyPageLayoutFinder);
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
    }

    protected static class TagLibraryLookupRegistrar implements ImportBeanDefinitionRegistrar {

        public static final Class<?>[] DEFAULT_TAGLIB_CLASSES=new Class<?>[] { GrailsLayoutTagLib.class, RenderTagLib.class, RenderGrailsLayoutTagLib.class };

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            if (!registry.containsBeanDefinition("gspTagLibraryLookup")) {
                GenericBeanDefinition beanDefinition = createBeanDefinition(StandaloneTagLibraryLookup.class);
                
                ManagedList<BeanDefinition> list = new ManagedList<BeanDefinition>();
                registerTagLibs(list);

                beanDefinition.getPropertyValues().addPropertyValue("tagLibInstances", list);

                registry.registerBeanDefinition("gspTagLibraryLookup", beanDefinition);
                registry.registerAlias("gspTagLibraryLookup", "tagLibraryLookup");
            }
        }

        protected void registerTagLibs(ManagedList<BeanDefinition> list) {
            for (Class<?> taglibClazz : DEFAULT_TAGLIB_CLASSES) {
                list.add(createBeanDefinition(taglibClazz));
            }
        }

        protected GenericBeanDefinition createBeanDefinition(Class<?> beanClass) {
            GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
            beanDefinition.setBeanClass(beanClass);
            beanDefinition.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_NAME);
            return beanDefinition;
        }
    }

    /**
     * {@link WebMvcAutoConfiguration} adds defaultViewResolver and viewResolver beans.
     * 
     *  This ImportBeanDefinitionRegistrar removes the defaultViewResolver and replaces 
     *  the viewResolver bean with GSP view resolver by default.
     *  
     *  The behavior of this class can be controlled with spring.gsp.removeDefaultViewResolver and
     *  spring.gsp.replaceViewResolverBean configuration properties.
     *
     */
    protected static class RemoveDefaultViewResolverRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {
        boolean removeDefaultViewResolverBean;
        boolean replaceViewResolverBean;
        
        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            if (removeDefaultViewResolverBean) {
                if (registry.containsBeanDefinition("defaultViewResolver")) {
                    registry.removeBeanDefinition("defaultViewResolver");
                }
            }
            if (replaceViewResolverBean) {
                if (registry.containsBeanDefinition("viewResolver")) {
                    registry.removeBeanDefinition("viewResolver");
                }
                registry.registerAlias("gspViewResolver", "viewResolver");
            }
        }

        @Override
        public void setEnvironment(Environment environment) {
            removeDefaultViewResolverBean = environment.getProperty("spring.gsp.removeDefaultViewResolverBean", Boolean.class, true);
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
            defaultProperties.put("grails.gsp.tldScanPattern","classpath*:/META-INF/spring*.tld,classpath*:/META-INF/fmt.tld,classpath*:/META-INF/c.tld,classpath*:/META-INF/c-1_0-rt.tld");
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

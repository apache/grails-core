package org.grails.plugins.web.controllers;

import grails.config.Settings;
import grails.core.GrailsApplication;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;
import org.grails.web.config.http.GrailsFilters;
import org.grails.web.filters.HiddenHttpMethodFilter;
import org.grails.web.servlet.mvc.GrailsDispatcherServlet;
import org.grails.web.servlet.mvc.GrailsWebRequestFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletRegistrationBean;
import org.springframework.boot.autoconfigure.web.servlet.HttpEncodingAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.filter.OrderedCharacterEncodingFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.EnumSet;

@AutoConfiguration(before = { HttpEncodingAutoConfiguration.class, WebMvcAutoConfiguration.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ControllersAutoConfiguration {

    @Value("${" + Settings.FILTER_ENCODING + ":utf-8}")
    private String filtersEncoding;

    @Value("${" + Settings.FILTER_FORCE_ENCODING + ":false}")
    private boolean filtersForceEncoding;

    @Value("${" + Settings.RESOURCES_CACHE_PERIOD + ":0}")
    private int resourcesCachePeriod;

    @Value("${" + Settings.RESOURCES_ENABLED + ":true}")
    private boolean resourcesEnabled;

    @Value("${" + Settings.RESOURCES_PATTERN + ":" + Settings.DEFAULT_RESOURCE_PATTERN + "}")
    private String resourcesPattern;

    @Value("${" + Settings.CONTROLLERS_UPLOAD_LOCATION + ":#{null}}")
    private String uploadTmpDir;

    @Value("${" + Settings.CONTROLLERS_UPLOAD_MAX_FILE_SIZE + ":128000}")
    private long maxFileSize;

    @Value("${" + Settings.CONTROLLERS_UPLOAD_MAX_REQUEST_SIZE + ":128000}")
    private long maxRequestSize;

    @Value("${" + Settings.CONTROLLERS_UPLOAD_FILE_SIZE_THRESHOLD + ":0}")
    private int fileSizeThreshold;

    @Value("${" + Settings.WEB_SERVLET_PATH + ":#{null}}")
    String grailsServletPath;

    @Bean
    @ConditionalOnMissingBean(CharacterEncodingFilter.class)
    public CharacterEncodingFilter characterEncodingFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        OrderedCharacterEncodingFilter characterEncodingFilter = new OrderedCharacterEncodingFilter();
        characterEncodingFilter.setEncoding(filtersEncoding);
        characterEncodingFilter.setForceEncoding(filtersForceEncoding);
        characterEncodingFilter.setOrder(GrailsFilters.CHARACTER_ENCODING_FILTER.getOrder());
        return characterEncodingFilter;
    }

    @Bean
    @ConditionalOnMissingBean(HiddenHttpMethodFilter.class)
    public FilterRegistrationBean<Filter> hiddenHttpMethodFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new HiddenHttpMethodFilter());
        registrationBean.addUrlPatterns(Settings.DEFAULT_WEB_SERVLET_PATH);
        registrationBean.setOrder(GrailsFilters.HIDDEN_HTTP_METHOD_FILTER.getOrder());
        return registrationBean;
    }

    @Bean
    @ConditionalOnMissingBean(GrailsWebRequestFilter.class)
    public FilterRegistrationBean<Filter> grailsWebRequestFilter(ApplicationContext applicationContext) {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        GrailsWebRequestFilter grailsWebRequestFilter = new GrailsWebRequestFilter();
        grailsWebRequestFilter.setApplicationContext(applicationContext);
        registrationBean.setFilter(grailsWebRequestFilter);
        registrationBean.setDispatcherTypes(EnumSet.of(
                DispatcherType.FORWARD,
                DispatcherType.INCLUDE,
                DispatcherType.REQUEST)
        );
        registrationBean.addUrlPatterns(Settings.DEFAULT_WEB_SERVLET_PATH);
        registrationBean.setOrder(GrailsFilters.GRAILS_WEB_REQUEST_FILTER.getOrder());
        return registrationBean;
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        if (uploadTmpDir == null) {
            uploadTmpDir = System.getProperty("java.io.tmpdir");
        }
        return new MultipartConfigElement(uploadTmpDir, maxFileSize, maxRequestSize, fileSizeThreshold);
    }

    @Bean
    public DispatcherServlet dispatcherServlet() {
        return new GrailsDispatcherServlet();
    }

    @Bean
    public DispatcherServletRegistrationBean dispatcherServletRegistration(GrailsApplication application, DispatcherServlet dispatcherServlet, MultipartConfigElement multipartConfigElement) {
        if (grailsServletPath == null) {
            boolean isTomcat = ClassUtils.isPresent("org.apache.catalina.startup.Tomcat", application.getClassLoader());
            grailsServletPath = isTomcat ? Settings.DEFAULT_TOMCAT_SERVLET_PATH : Settings.DEFAULT_WEB_SERVLET_PATH;
        }
        DispatcherServletRegistrationBean dispatcherServletRegistration = new DispatcherServletRegistrationBean(dispatcherServlet, grailsServletPath);
        dispatcherServletRegistration.setLoadOnStartup(2);
        dispatcherServletRegistration.setAsyncSupported(true);
        dispatcherServletRegistration.setMultipartConfig(multipartConfigElement);
        return dispatcherServletRegistration;
    }

    @Bean
    public GrailsWebMvcConfigurer webMvcConfig() {
        return new GrailsWebMvcConfigurer(resourcesCachePeriod, resourcesEnabled, resourcesPattern);
    }

    static class GrailsWebMvcConfigurer implements WebMvcConfigurer {

        private static final String[] SERVLET_RESOURCE_LOCATIONS = new String[] { "/" };

        private static final String[] CLASSPATH_RESOURCE_LOCATIONS = new String[] {
                "classpath:/META-INF/resources/", "classpath:/resources/",
                "classpath:/static/", "classpath:/public/"
        };

        private static final String[] RESOURCE_LOCATIONS;

        static {
            RESOURCE_LOCATIONS = new String[CLASSPATH_RESOURCE_LOCATIONS.length
                    + SERVLET_RESOURCE_LOCATIONS.length];
            System.arraycopy(SERVLET_RESOURCE_LOCATIONS, 0, RESOURCE_LOCATIONS, 0,
                    SERVLET_RESOURCE_LOCATIONS.length);
            System.arraycopy(CLASSPATH_RESOURCE_LOCATIONS, 0, RESOURCE_LOCATIONS,
                    SERVLET_RESOURCE_LOCATIONS.length, CLASSPATH_RESOURCE_LOCATIONS.length);
        }

        boolean addMappings;
        Integer cachePeriod;
        String resourcesPattern;

        GrailsWebMvcConfigurer(Integer cachePeriod, Boolean addMappings, String resourcesPattern) {
            this.addMappings = addMappings;
            this.cachePeriod = cachePeriod;
            this.resourcesPattern = resourcesPattern;
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            if (!addMappings) {
                return;
            }

            if (!registry.hasMappingForPattern("/webjars/**")) {
                registry.addResourceHandler("/webjars/**")
                        .addResourceLocations("classpath:/META-INF/resources/webjars/")
                        .setCachePeriod(cachePeriod);
            }
            if (!registry.hasMappingForPattern(resourcesPattern)) {
                registry.addResourceHandler(resourcesPattern)
                        .addResourceLocations(RESOURCE_LOCATIONS)
                        .setCachePeriod(cachePeriod);
            }
        }
    }
}

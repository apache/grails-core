package org.grails.plugins.web.mapping;

import grails.config.Settings;
import grails.util.Environment;
import grails.web.CamelCaseUrlConverter;
import grails.web.HyphenatedUrlConverter;
import grails.web.UrlConverter;
import grails.web.mapping.LinkGenerator;
import grails.web.mapping.UrlMappings;
import grails.web.mapping.cors.GrailsCorsConfiguration;
import grails.web.mapping.cors.GrailsCorsFilter;
import org.grails.web.mapping.CachingLinkGenerator;
import org.grails.web.mapping.DefaultLinkGenerator;
import org.grails.web.mapping.mvc.UrlMappingsInfoHandlerAdapter;
import org.grails.web.mapping.servlet.UrlMappingsErrorPageCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({ GrailsCorsConfiguration.class })
public class UrlMappingsAutoConfiguration {
    @Value("${" + Settings.WEB_LINK_GENERATOR_USE_CACHE + ":#{null}}")
    private Boolean cacheUrls;

    @Value("${" + Settings.SERVER_URL + ":#{null}}")
    private String serverURL;

    @Bean(UrlConverter.BEAN_NAME)
    @ConditionalOnMissingBean(name = UrlConverter.BEAN_NAME)
    @ConditionalOnProperty(name = Settings.WEB_URL_CONVERTER, havingValue = "camelCase", matchIfMissing = true)
    public UrlConverter camelCaseUrlConverter() {
        return new CamelCaseUrlConverter();
    }

    @Bean(UrlConverter.BEAN_NAME)
    @ConditionalOnMissingBean(name = UrlConverter.BEAN_NAME)
    @ConditionalOnProperty(name = Settings.WEB_URL_CONVERTER, havingValue = "hyphenated")
    public UrlConverter hyphenatedUrlConverter() {
        return new HyphenatedUrlConverter();
    }

    @Bean
    @ConditionalOnMissingBean(name = LinkGenerator.BEAN_NAME)
    public LinkGenerator grailsLinkGenerator() {
        if (cacheUrls == null) {
            cacheUrls = !Environment.isDevelopmentMode() && !Environment.getCurrent().isReloadEnabled();
        }
        return cacheUrls? new CachingLinkGenerator(serverURL) : new DefaultLinkGenerator(serverURL);
    }

    @Bean
    @ConditionalOnProperty(name = Settings.SETTING_CORS_FILTER, havingValue = "true", matchIfMissing = true)
    public GrailsCorsFilter grailsCorsFilter(GrailsCorsConfiguration grailsCorsConfiguration) {
        return new GrailsCorsFilter(grailsCorsConfiguration);
    }

    @Bean
    public UrlMappingsErrorPageCustomizer urlMappingsErrorPageCustomizer(ObjectProvider<UrlMappings> urlMappingsProvider) {
        UrlMappingsErrorPageCustomizer errorPageCustomizer = new UrlMappingsErrorPageCustomizer();
        errorPageCustomizer.setUrlMappings(urlMappingsProvider.getIfAvailable());
        return errorPageCustomizer;
    }

    @Bean
    public UrlMappingsInfoHandlerAdapter urlMappingsInfoHandlerAdapter() {
        return new UrlMappingsInfoHandlerAdapter();
    }
}

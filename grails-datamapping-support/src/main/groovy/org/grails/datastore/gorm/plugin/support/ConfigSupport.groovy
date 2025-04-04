package org.grails.datastore.gorm.plugin.support

import org.grails.config.PropertySourcesConfig
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.support.ConfigurableConversionService
import org.springframework.core.env.PropertyResolver

/**
 * Support for configuration when developing Grails plugins
 *
 * @author Graeme Rocher
 * @since 6.0
 */
class ConfigSupport {

    /**
     * Workaround method because Grails' config doesn't convert strings to classes correctly
     *
     * @param config The config
     * @param applicationContext The application context
     */
    static void prepareConfig(PropertyResolver config, ConfigurableApplicationContext applicationContext) {
        if(config instanceof PropertySourcesConfig) {
            ConfigurableConversionService conversionService = applicationContext.getEnvironment().getConversionService()
            conversionService.addConverter(new Converter<String, Class>() {
                @Override
                Class convert(String source) {
                    Class.forName(source)
                }
            })
            ((PropertySourcesConfig)config).setConversionService(conversionService)
        }
    }
}

package org.grails.gorm.graphql.plugin

import grails.config.Config
import grails.core.GrailsApplication
import groovy.transform.CompileStatic
import org.grails.plugins.databinding.DataBindingGrailsPlugin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties

import javax.annotation.PostConstruct

@CompileStatic
@ConfigurationProperties(prefix = 'grails.gorm.graphql')
class GrailsGraphQLConfiguration {

    @Autowired
    private GrailsApplication grailsApplication

    Boolean enabled = true

    List<String> dateFormats

    Boolean dateFormatLenient

    Map<String, Class> listArguments

    Boolean browser

    @PostConstruct
    void init() {
        Config config = grailsApplication.config
        if (dateFormats == null) {
            dateFormats = config.getProperty('grails.databinding.dateFormats', List, DataBindingGrailsPlugin.DEFAULT_DATE_FORMATS)
        }
        if (dateFormatLenient == null) {
            dateFormatLenient = config.getProperty('grails.databinding.dateParsingLenient', Boolean, false)
        }
        if (browser == null) {
            browser = false
        }
    }
}

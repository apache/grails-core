package demo

import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import groovy.transform.CompileStatic

@CompileStatic
class ConfigService implements GrailsConfigurationAware {

    Boolean allowSignup

    @Override
    void setConfiguration(Config co) {
        allowSignup = co.getProperty('mc.allow.signup', Boolean)
    }

    boolean isSignupAllowed() {
        allowSignup
    }
}
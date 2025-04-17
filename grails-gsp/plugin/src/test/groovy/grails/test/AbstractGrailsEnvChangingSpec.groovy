package grails.test

import grails.util.Environment
import spock.lang.Shared
import spock.lang.Specification

/**
 * Grails internal class for running Spec in a defined Grails Environment
 *
 * returns Grails Environment (System Properties) back to original after running the Spec.
 *
 * For example
 * String grailsEnvName = "test"
 * runs the test in "test" Grails Environment
 *
 * @author Lari Hotari
 */
abstract class AbstractGrailsEnvChangingSpec extends Specification {
    @Shared
    String originalGrailsEnv
    @Shared
    String originalGrailsEnvDefault
    static List<String> grailsEnvs = [Environment.DEVELOPMENT.name, Environment.TEST.name, Environment.PRODUCTION.name]

    String getGrailsEnvName() {
        null
    }

    def setup() {
        // set grails.env before each test
        changeGrailsEnv(grailsEnvName)
    }

    def setupSpec() {
        // save grails.env and grails.env.default keys before running this spec
        originalGrailsEnv = System.getProperty(Environment.KEY)
        originalGrailsEnvDefault = System.getProperty(Environment.DEFAULT)
    }

    def cleanupSpec() {
        // reset grails.env and grails.env.default keys after running this spec
        resetGrailsEnvironment()
    }

    protected void changeGrailsEnv(String newEnv) {
        resetGrailsEnvironment()
        if (newEnv != null) {
            System.setProperty(Environment.KEY, newEnv)
        }
    }

    protected void resetGrailsEnvironment() {
        if (originalGrailsEnv != null) {
            System.setProperty(Environment.KEY, originalGrailsEnv)
        } else {
            System.clearProperty(Environment.KEY)
        }

        if (originalGrailsEnvDefault != null) {
            System.setProperty(Environment.DEFAULT, originalGrailsEnvDefault)
        } else {
            System.clearProperty(Environment.DEFAULT)
        }
    }

    protected createCombinationsForGrailsEnvs(params) {
        [params,grailsEnvs].combinations().collect { it.flatten() }
    }
}

package org.grails.datastore.mapping.core.grailsversion

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by jameskleeh on 1/17/17.
 */
class GrailsVersionSpec extends Specification {

    @Unroll
    void "isAtLeast(#requiredVersion) => #expected"(String requiredVersion, boolean expected) {
        expect:
        expected == GrailsVersion.isAtLeast(requiredVersion)

        where:
        requiredVersion | expected
        "3.2.0"         | true
        "3.1.0"         | true
        "3.3.0"         | true
        "7.0.0"         | false
    }

    @Unroll
    void "isAtLeastMajorMinor(#grailsVersion, #majorVersion, #minorVersion) => #expected"(String grailsVersion, int majorVersion, int minorVersion, boolean expected) {
        expect:
        expected == GrailsVersion.isAtLeastMajorMinor(grailsVersion, majorVersion, minorVersion)

        where:
        grailsVersion    | majorVersion | minorVersion | expected
        "4.0.0-SNAPSHOT" | 3            | 3            | true
        "4.0.0-SNAPSHOT" | 4            | 0            | true
        "3.3.0-SNAPSHOT" | 3            | 3            | true
        "3.3.0-SNAPSHOT" | 3            | 4            | false
        "3.3.0-SNAPSHOT" | 3            | 2            | true
        "3.3.0-SNAPSHOT" | 3            | 2            | true
    }

    @Unroll
    void "test isAtLeast(#version, #requiredVersion) => expected"(String version,
                                                                  String requiredVersion,
                                                                  boolean expected) {
        expect:
        expected == GrailsVersion.isAtLeast(version, requiredVersion)

        where:
        version          | requiredVersion  | expected
        "3.3.0"          | "3.3.0-SNAPSHOT" | true
        "3.3.0"          | "3.3.0.M1"       | true
        "3.3.0-SNAPSHOT" | "3.3.0"          | false
        "3.3.0"          | "3.3.0"          | true
    }

    void "test compareTo"() {
        expect:
        new GrailsVersion(greater) > new GrailsVersion(lesser)

        where:
        greater          | lesser
        "3.0.0"          | "2.99.99-SNAPSHOT"
        "3.0.0"          | "2.99.99"
        "3.0.1"          | "3.0.1-SNAPSHOT"
        "3.1.2"          | "3.1.1"
        "3.2.2"          | "3.1.2"
        "4.1.1"          | "3.1.1"
        "3.0.0.RC2"      | "3.0.0.RC1"
        "3.0.0.M3"       | "3.0.0.M2"
        "3.0.0.RC1"      | "3.0.0.M9"
        "3.0.0-SNAPSHOT" | "3.0.0.RC9"
    }

    void "test compareTo equal"() {
        expect:
        new GrailsVersion(left) == new GrailsVersion(right)

        where:
        left             | right
        "3.0.0"          | "3.0.0"
        "3.0.0.M2"       | "3.0.0.M2"
        "3.0.0.RC2"      | "3.0.0.RC2"
        "3.0.0-SNAPSHOT" | "3.0.0-SNAPSHOT"
    }

    void "test illegal argument to constructor"() {
        when:
        new GrailsVersion("x")

        then:
        thrown(IllegalArgumentException)

        when:
        new GrailsVersion("3.1.x")

        then:
        thrown(NumberFormatException)

        when:
        new GrailsVersion("3.x.1")

        then:
        thrown(NumberFormatException)

        when:
        new GrailsVersion("x.1.1")

        then:
        thrown(NumberFormatException)

        when:
        new GrailsVersion("3.1.1.Mu")

        then:
        thrown(IllegalArgumentException)

        when:
        new GrailsVersion("3.1.1.RCu")

        then:
        thrown(IllegalArgumentException)
    }

}
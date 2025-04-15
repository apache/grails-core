package grails.doc.dropdown

import spock.lang.Specification
import spock.lang.Unroll

class SoftwareVersionSpec extends Specification {

    @Unroll
    void "it can parse version: #semver"(String semver, SoftwareVersion expected) {
        when:
        expected.versionText = semver
        SoftwareVersion softwareVersion = SoftwareVersion.build(semver)

        then:
        softwareVersion == expected

        where:
        semver                  || expected
        "1.0.0"                 || new SoftwareVersion(major: 1, minor: 0, patch: 0)

        "1.0.0.M1"              || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("M1"))
        "1.0.0.RC1"             || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("RC1"))
        "1.0.0.BUILD-SNAPSHOT"  || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("BUILD-SNAPSHOT"))

        "1.0.0-M1"              || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("M1"))
        "1.0.0-RC1"             || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("RC1"))
        "1.0.0-BUILD-SNAPSHOT"  || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("BUILD-SNAPSHOT"))

    }
}

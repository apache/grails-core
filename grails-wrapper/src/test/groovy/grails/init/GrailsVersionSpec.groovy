package grails.init

import spock.lang.Specification
import spock.lang.Unroll

class GrailsVersionSpec extends Specification {

    @Unroll
    def "grails version #version"(String version, String major, String minor, String patch, GrailsReleaseType releaseType, Integer candidate) {
        when:
        GrailsVersion grailsVersion = new GrailsVersion(version)

        then:
        noExceptionThrown()
        grailsVersion.version == version
        grailsVersion.major == major as int
        grailsVersion.minor == minor as int
        grailsVersion.patch == patch as int
        grailsVersion.releaseType == releaseType
        grailsVersion.candidate == candidate as Integer

        where:
        version          | major | minor | patch | releaseType                 | candidate
        '7.0.0'          | '7'   | '0'   | '0'   | GrailsReleaseType.RELEASE   | null
        '7.0.1'          | '7'   | '0'   | '1'   | GrailsReleaseType.RELEASE   | null
        '7.2.0'          | '7'   | '2'   | '0'   | GrailsReleaseType.RELEASE   | null
        '7.0.0-SNAPSHOT' | '7'   | '0'   | '0'   | GrailsReleaseType.SNAPSHOT  | null
        '7.0.0-RC1'      | '7'   | '0'   | '0'   | GrailsReleaseType.RC        | 1
        '7.0.0-M2'       | '7'   | '0'   | '0'   | GrailsReleaseType.MILESTONE | 2
    }

    def "comparison checks"() {
        expect:
        new GrailsVersion('7.0.0') < new GrailsVersion('7.0.1')
        new GrailsVersion('7.0.1') > new GrailsVersion('7.0.0')
        new GrailsVersion('7.0.0') < new GrailsVersion('7.1.0')
        new GrailsVersion('8.0.0') > new GrailsVersion('7.0.0')
        new GrailsVersion('7.0.0') > new GrailsVersion('7.0.0-SNAPSHOT')
        new GrailsVersion('7.0.0') > new GrailsVersion('7.0.0-RC1')
        new GrailsVersion('7.0.0') > new GrailsVersion('7.0.0-M1')
        new GrailsVersion('7.0.0-RC1') > new GrailsVersion('7.0.0-M1')
        new GrailsVersion('7.0.0-RC2') > new GrailsVersion('7.0.0-RC1')
        new GrailsVersion('7.0.0-RC1') > new GrailsVersion('7.0.0-SNAPSHOT')
        new GrailsVersion('7.0.0-M2') > new GrailsVersion('7.0.0-M1')
        new GrailsVersion('7.0.0-M1') > new GrailsVersion('7.0.0-SNAPSHOT')
    }

    def "sorted"() {
        expect:
        [new GrailsVersion('7.0.0'), new GrailsVersion('7.0.0-RC1'), new GrailsVersion('7.0.0-M1'), new GrailsVersion('7.0.0-SNAPSHOT')].sort() == [
            new GrailsVersion('7.0.0-SNAPSHOT'),
            new GrailsVersion('7.0.0-M1'),
            new GrailsVersion('7.0.0-RC1'),
            new GrailsVersion('7.0.0')
        ]
    }
}

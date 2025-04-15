package grails.io

import org.grails.io.support.Resource
import spock.lang.Specification

class IOUtilsSpec extends Specification{

    void "Test findClassResource finds a class resource"() {
        expect:
        IOUtils.findClassResource(Resource)
        IOUtils.findClassResource(Resource).path.contains('grails-gradle/model')
    }

    void "Test findJarResource finds a JAR resource"() {
        expect:
        IOUtils.findJarResource(Specification)
        IOUtils.findJarResource(Specification).path.endsWith('spock-core-2.3-groovy-3.0.jar!/')
    }
}

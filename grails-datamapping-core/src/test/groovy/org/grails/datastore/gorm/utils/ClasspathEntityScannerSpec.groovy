package org.grails.datastore.gorm.utils

import grails.gorm.annotation.Entity
import spock.lang.Specification

/**
 * Created by graemerocher on 18/11/16.
 */
class ClasspathEntityScannerSpec extends Specification {

    void "test classpath entity scanner"() {
        when:"the classpath is scanned"
        def scanner = new ClasspathEntityScanner()
        def results = scanner.scan(ClasspathEntityScannerSpec.package)

        then:"The results are correct"
        results.size() == 1
        results.first() == TestEntity
    }
}

@Entity
class TestEntity {
    String name
}

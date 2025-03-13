package grails.gorm.tests

import grails.gorm.tck.FirstAndLastMethodSpec
import grails.gorm.tck.NotInListSpec
import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite

/**
 * Created by graemerocher on 06/07/2016.
 */
@Suite
@SelectClasses([FirstAndLastMethodSpec])
class HibernateSuite {
}

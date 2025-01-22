package org.grails.datastore.gorm

import grails.gorm.tests.NotInListSpec
import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite

/**
 * @author graemerocher
 */
@Suite
@SelectClasses([NotInListSpec])
class SimpleMapTestSuite {
}

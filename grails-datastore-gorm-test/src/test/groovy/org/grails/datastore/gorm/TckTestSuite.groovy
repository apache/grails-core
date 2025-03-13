package org.grails.datastore.gorm

import grails.gorm.tck.NotInListSpec
import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite

/**
 * Use this class to run tck classes against the current implementation.
 *
 * @author graemerocher
 */
@Suite
@SelectClasses([NotInListSpec])
class TckTestSuite {
}

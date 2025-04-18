package org.grails.datastore.gorm.neo4j

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.junit.runners.Suite.SuiteClasses
import grails.gorm.tests.*

@RunWith(Suite)
@SuiteClasses([
CircularOneToManySpec
])
class Neo4jSuite {
}

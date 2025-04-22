package org.apache.grails.gradle.tasks.bom

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(includes = ['groupId', 'artifactId'])
@CompileStatic
@ToString
class CoordinateHolder {
    String groupId
    String artifactId

    String getCoordinatesWithoutVersion() {
        "$groupId:$artifactId" as String
    }
}
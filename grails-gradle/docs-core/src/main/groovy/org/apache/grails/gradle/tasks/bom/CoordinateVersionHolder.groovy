package org.apache.grails.gradle.tasks.bom

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.gradle.api.GradleException

@EqualsAndHashCode(includes = ['version'], callSuper = true)
@CompileStatic
@ToString
class CoordinateVersionHolder extends CoordinateHolder {
    String version

    CoordinateHolder toCoordinateHolder() {
        new CoordinateHolder(groupId: groupId, artifactId: artifactId)
    }

    String getCoordinates() {
        if (!version) {
            throw new GradleException("Constraint does not have a version: ${this}")
        }

        "$groupId:$artifactId:$version" as String
    }
}
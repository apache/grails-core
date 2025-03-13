package org.grails.plugins.databasemigration.testing.annotation

import org.grails.plugins.databasemigration.testing.OutputCaptureExtension
import org.spockframework.runtime.extension.ExtensionAnnotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@ExtensionAnnotation(OutputCaptureExtension)
@interface OutputCapture {

}
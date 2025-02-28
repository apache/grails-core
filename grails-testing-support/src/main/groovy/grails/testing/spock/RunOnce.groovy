package grails.testing.spock

import org.grails.testing.spock.RunOnceExtension
import org.spockframework.runtime.extension.ExtensionAnnotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * This annotation may be applied to fixture methods in a Spock Spec that should
 * be run once and only.
 *
 * @see OnceBefore
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ExtensionAnnotation(RunOnceExtension)
@interface RunOnce {
}

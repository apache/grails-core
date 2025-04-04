package grails.gorm.dirty.checking

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * A marker annotation added to methods that are dirty checked
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD])
@interface DirtyCheckedProperty {
}
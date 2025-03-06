package grails.views.gradle.util

import groovy.transform.CompileStatic

/**
 * This class is copying some methods from GrailsNameUtils in grails-bootstrap.
 * This is done to avoid a dependency on grails-bootstrap.
 * GrailsNameUtils in grails-bootstrap is probably a candidate for moving to a future "grails-common" module.
 */
@CompileStatic
class GrailsNameUtils {

    /**
     * Returns the class name for the given logical name and trailing name. For example "person" and "Controller" would evaluate to "PersonController"
     *
     * @param logicalName The logical name
     * @param trailingName The trailing name
     * @return The class name
     */
    static String getClassName(String logicalName, String trailingName) {
        if (isBlank(logicalName)) {
            throw new IllegalArgumentException('Argument [logicalName] cannot be null or blank');
        }

        String className = logicalName.substring(0,1).toUpperCase(Locale.ENGLISH) + logicalName.substring(1);
        if (trailingName != null) {
            className = className + trailingName;
        }
        return className;
    }

    /**
     * Return the class name for the given logical name. For example "person" would evaluate to "Person"
     *
     * @param logicalName The logical name
     * @return The class name
     */
    static String getClassName(String logicalName) {
        return getClassName(logicalName, '');
    }

    /**
     * <p>Determines whether a given string is <code>null</code>, empty,
     * or only contains whitespace. If it contains anything other than
     * whitespace then the string is not considered to be blank and the
     * method returns <code>false</code>.</p>
     * <p>We could use Commons Lang for this, but we don't want GrailsNameUtils
     * to have a dependency on any external library to minimise the number of
     * dependencies required to bootstrap Grails.</p>
     * @param str The string to test.
     * @return <code>true</code> if the string is <code>null</code>, or
     * blank.
     */
    static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

}


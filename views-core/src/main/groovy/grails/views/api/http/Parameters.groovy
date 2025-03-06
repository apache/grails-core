package grails.views.api.http

import groovy.transform.CompileStatic

/**
 * Represents HTTP parameters
 *
 * @author Graeme Rocher
 * @since 1.1.0
 */
@CompileStatic
interface Parameters {

    /**
     *
     * @return The parameter names
     */
    Set<String> keySet()

    /**
     * @return Whether or not the key exists
     */
    boolean containsKey(Object key)

    /**
     * Obtains the value of a parameter
     *
     * @param name The name of the parameter
     * @return The value or null if it doesn't exist
     */
    String get(String name)

    /**
     * The same as {@link #get(java.lang.String)}
     * @param key The key
     * @return The value
     */
    String getAt(Object key)

    /**
     * The same as {@link #get(java.lang.String)}
     */
    Object getProperty(String name)

    /**
     * Obtains the value of a parameter
     *
     * @param name The name of the parameter
     * @param defaultValue The value to return if it doesn't exist
     * @return The value or the default value
     */
    String get(String name, String defaultValue)

    /**
     * Obtains the value of a parameter as a byte
     *
     * @param name The name of the parameter
     * @return The value or null
     */
    Byte 'byte'(String name)

    /**
     * Obtains the value of a parameter as a byte
     *
     * @param name The name of the parameter
     * @param defaultValue The value to return if it doesn't exist
     * @return The value or the default value
     */
    Byte 'byte'(String name, Integer defaultValue)

    /**
     * Obtains the value of a parameter as a character
     *
     * @param name The name of the parameter
     * @return The value or null
     */
    Character 'char'(String name)

    /**
     * Obtains the value of a parameter as a character
     *
     * @param name The name of the parameter
     * @param defaultValue The value to return if it doesn't exist
     * @return The value or the default value
     */
    Character 'char'(String name, Character defaultValue)

    /**
     * Obtains the value of a parameter as an integer
     *
     * @param name The name of the parameter
     * @return The value or null if it doesn't exist
     */
    Integer 'int'(String name)

    /**
     * Obtains the value of a parameter as an integer
     *
     * @param name The name of the parameter
     * @param defaultValue The value to return if it doesn't exist
     * @return The value or the default value
     */
    Integer 'int'(String name, Integer defaultValue)

    /**
     * Obtains the value of a parameter as a Long
     *
     * @param name The name of the parameter
     * @return The value or null
     */
    Long 'long'(String name)

    /**
     * Obtains the value of a parameter as a Long
     *
     * @param name The name of the parameter
     * @param defaultValue The value to return if it doesn't exist
     * @return The value or the default value
     */
    Long 'long'(String name, Long defaultValue)

    /**
     * Obtains the value of a parameter as a Short
     *
     * @param name The name of the parameter
     * @return The value or null
     */
    Short 'short'(String name)

    /**
     * Obtains the value of a parameter as a character
     *
     * @param name The name of the parameter
     * @param defaultValue The value to return if it doesn't exist
     * @return The value or the default value
     */
    Short 'short'(String name, Integer defaultValue)

    /**
     * Obtains the value of a parameter as a Double
     *
     * @param name The name of the parameter
     * @return The value or null
     */
    Double 'double'(String name)

    /**
     * Obtains the value of a parameter as a Double
     *
     * @param name The name of the parameter
     * @param defaultValue The value to return if it doesn't exist
     * @return The value or the default value
     */
    Double 'double'(String name, Double defaultValue)

    /**
     * Obtains the value of a parameter as a Float
     *
     * @param name The name of the parameter
     * @return The value or null
     */
    Float 'float'(String name)

    /**
     * Obtains the value of a parameter as a Float
     *
     * @param name The name of the parameter
     * @param defaultValue The value to return if it doesn't exist
     * @return The value or the default value
     */
    Float 'float'(String name, Float defaultValue)

    /**
     * Obtains the value of a parameter as a Boolean
     *
     * @param name The name of the parameter
     * @return The value or null
     */
    Boolean 'boolean'(String name)

    /**
     * Obtains the value of a parameter as a Boolean
     *
     * @param name The name of the parameter
     * @param defaultValue The value to return if it doesn't exist
     * @return The value or the default value
     */
    Boolean 'boolean'(String name, Boolean defaultValue)

    /**
     * Obtains the value of a parameter as a Date
     *
     * @param name The name of the parameter
     * @return The value or null
     */
    Date date(String name)

    /**
     * Obtains the value of a parameter as a Date
     *
     * @param name The name of the parameter
     * @param format The format
     * @return The value or null
     */
    Date date(String name, String format)

    /**
     * Returns value of the parameter as a list of values
     *
     * @param name The name of the parameter
     * @return A list of values
     */
    List<String> list(String name)

    /**
     *
     * @return The parameters as a boolean value. True if not empty
     */
    boolean asBoolean()

    /**
     * @return Whether there are any parameters
     */
    boolean isEmpty()
}
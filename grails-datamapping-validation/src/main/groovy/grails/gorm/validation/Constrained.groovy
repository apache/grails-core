package grails.gorm.validation

/**
 * Represents a constrained object
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface Constrained {

    /**
     * @return Returns the maximum possible value.
     */
    Comparable getMax()
    /**
     * @return Returns the minimum possible value.
     */
    Comparable getMin()
    /**
     * @return Constrains the be within the list of given values
     */
    List getInList()
    /**
     * @return Constrains the be within the range of given values
     */
    Range getRange()
    /**
     * @return The scale for decimal values
     */
    Integer getScale()
    /**
     * @return A range which represents the size constraints from minimum to maximum value
     */
    Range getSize()
    /**
     * @return Whether blank values are allowed
     */
    boolean isBlank()
    /**
     * @return Whether this is an email
     */
    boolean isEmail()
    /**
     * @return Whether this is a credit card string
     */
    boolean isCreditCard()
    /**
     * @return The string this constrained matches
     */
    String getMatches()
    /**
     * @return The value this constrained should not be equal to
     */
    Object getNotEqual()
    /**
     * @return The maximum size
     */
    Integer getMaxSize()
    /**
     * @return The minimum size
     */
    Integer getMinSize()
    /**
     * @return Whether the value is nullable
     */
    boolean isNullable()
    /**
     * @return Whether the value is a URL
     */
    boolean isUrl()
    /**
     * @return Whether the value should be displayed
     */
    boolean isDisplay()
    /**
     * @return Whether the value is editable
     */
    boolean isEditable()
    /**
     * @return The order of the value
     */
    int getOrder()
    /**
     * @return The format of the value
     */
    String getFormat()

    /**
     * @return The widget of the property
     */
    String getWidget()
    /**
     * @return Whether the value is a password or not
     */
    boolean isPassword()

    /**
     * Whether the given constraint has been applied
     *
     * @param constraint The name of the constraint
     * @return True it has
     */
    boolean hasAppliedConstraint(String constraint)

    /**
     * Applies the given constraint
     *
     * @param constraintName The name of the constraint
     * @param constrainingValue The constraining value
     */
    void applyConstraint(String constraintName, Object constrainingValue)

    /**
     * @return The owning class
     */
    Class getOwner()
}
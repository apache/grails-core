package grails.gorm.validation

import grails.gorm.validation.exceptions.ValidationConfigurationException
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import org.grails.datastore.gorm.validation.constraints.BlankConstraint
import org.grails.datastore.gorm.validation.constraints.InListConstraint
import org.grails.datastore.gorm.validation.constraints.MatchesConstraint
import org.grails.datastore.gorm.validation.constraints.MaxConstraint
import org.grails.datastore.gorm.validation.constraints.MaxSizeConstraint
import org.grails.datastore.gorm.validation.constraints.MinConstraint
import org.grails.datastore.gorm.validation.constraints.MinSizeConstraint
import org.grails.datastore.gorm.validation.constraints.NotEqualConstraint
import org.grails.datastore.gorm.validation.constraints.NullableConstraint
import org.grails.datastore.gorm.validation.constraints.RangeConstraint
import org.grails.datastore.gorm.validation.constraints.ScaleConstraint
import org.grails.datastore.gorm.validation.constraints.SizeConstraint
import org.grails.datastore.gorm.validation.constraints.factory.ConstraintFactory
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.springframework.validation.Errors

/**
 * Default implementation of the {@link ConstrainedProperty} interface
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
@Slf4j
@ToString(includes = ['owningClass', 'propertyType', 'propertyName', 'appliedConstraints'])
class DefaultConstrainedProperty implements ConstrainedProperty {

    /**
     * The owning class
     */
    final Class<?> owningClass
    /**
     * The property type
     */
    final Class<?> propertyType
    /**
     * the property name
     */
    final String propertyName

    protected final ConstraintRegistry constraintRegistry
    protected final Map<String, Constraint> appliedConstraints = new LinkedHashMap<String, Constraint>()

    // simple constraints
    /** whether the property should be displayed */
    boolean display = true
    /**
     * whether the property is editable
     */
    boolean editable = true
    /**
     * The order of the property
     */
    int order
    /**
     * the format of the property (for example a date pattern)
     */
    String format
    /**
     * The widget to use to render the property
     */
    String widget
    /**
     * whether the property is a password
     */
    boolean password

    Map attributes = Collections.EMPTY_MAP // a map of attributes of property

    private Map<String, Object> metaConstraints = new HashMap<String, Object>()
    private final static ClassPropertyFetcher PROPERTY_FETCHER = ClassPropertyFetcher.forClass(DefaultConstrainedProperty)

    /**
     * Constructs a new ConstrainedProperty for the given arguments.
     *
     * @param owningClass The owning class
     * @param propertyName The name of the property
     * @param propertyType The property type
     */
    DefaultConstrainedProperty(Class<?> owningClass, String propertyName, Class<?> propertyType, ConstraintRegistry constraintRegistry) {
        this.owningClass = owningClass
        this.propertyName = propertyName
        this.propertyType = propertyType
        this.constraintRegistry = constraintRegistry

    }



    /**
     * @return Returns the appliedConstraints.
     */
    @Override
    Collection<Constraint> getAppliedConstraints() {
        return appliedConstraints.values()
    }

    /**
     * Obtains an applied constraint by name.
     * @param name The name of the constraint
     * @return The applied constraint
     */
    @Override
    Constraint getAppliedConstraint(String name) {
        return appliedConstraints.get(name)
    }

    /**
     * @param constraintName The name of the constraint to check
     * @return Returns true if the specified constraint name is being applied to this property
     */
    @Override
    boolean hasAppliedConstraint(String constraintName) {
        return appliedConstraints.containsKey(constraintName)
    }

    /**
     * @return Returns the max.
     */
    @Override
    Comparable getMax() {
        Comparable maxValue = null

        MaxConstraint maxConstraint = (MaxConstraint)appliedConstraints.get(MAX_CONSTRAINT)
        RangeConstraint rangeConstraint = (RangeConstraint)appliedConstraints.get(RANGE_CONSTRAINT)

        if (maxConstraint != null || rangeConstraint != null) {
            Comparable maxConstraintValue = maxConstraint == null ? null : maxConstraint.getMaxValue()
            Comparable rangeConstraintHighValue = rangeConstraint == null ? null : rangeConstraint.getRange().getTo()

            if (maxConstraintValue != null && rangeConstraintHighValue != null) {
                maxValue = (maxConstraintValue.compareTo(rangeConstraintHighValue) < 0) ? maxConstraintValue : rangeConstraintHighValue
            }
            else if (maxConstraintValue == null && rangeConstraintHighValue != null) {
                maxValue = rangeConstraintHighValue
            }
            else if (maxConstraintValue != null && rangeConstraintHighValue == null) {
                maxValue = maxConstraintValue
            }
        }

        return maxValue
    }

    /**
     * @param max The max to set.
     */
    @SuppressWarnings("rawtypes")
    void setMax(Comparable max) {
        String constraintName = MAX_CONSTRAINT
        if (max == null) {
            appliedConstraints.remove(constraintName)
            return
        }

        if (!propertyType.equals(max.getClass())) {
            throw new MissingPropertyException(constraintName,propertyType)
        }

        Range r = getRange()
        if (r != null) {
            log.warn("Range constraint already set ignoring constraint [" + constraintName + "] for value [" + max + "]")
            return
        }

        applyConstraintInternal(constraintName, max)
    }

    /**
     * @return Returns the min.
     */
    @Override
    Comparable getMin() {
        Comparable minValue = null

        MinConstraint minConstraint = (MinConstraint)appliedConstraints.get(MIN_CONSTRAINT)
        RangeConstraint rangeConstraint = (RangeConstraint)appliedConstraints.get(RANGE_CONSTRAINT)

        if (minConstraint != null || rangeConstraint != null) {
            Comparable minConstraintValue = minConstraint != null ? minConstraint.getMinValue() : null
            Comparable rangeConstraintLowValue = rangeConstraint != null ? rangeConstraint.getRange().getFrom() : null

            if (minConstraintValue != null && rangeConstraintLowValue != null) {
                minValue = (minConstraintValue.compareTo(rangeConstraintLowValue) > 0) ? minConstraintValue : rangeConstraintLowValue
            }
            else if (minConstraintValue == null && rangeConstraintLowValue != null) {
                minValue = rangeConstraintLowValue
            }
            else if (minConstraintValue != null && rangeConstraintLowValue == null) {
                minValue = minConstraintValue
            }
        }

        return minValue
    }

    /**
     * @param min The min to set.
     */
    @SuppressWarnings("rawtypes")
    void setMin(Comparable min) {
        if (min == null) {
            appliedConstraints.remove(MIN_CONSTRAINT)
            return
        }

        if (!propertyType.equals(min.getClass())) {
            throw new MissingPropertyException(MIN_CONSTRAINT,propertyType)
        }

        Range r = getRange()
        if (r != null) {
            log.warn("Range constraint already set ignoring constraint ["+MIN_CONSTRAINT+"] for value ["+min+"]")
            return
        }

        applyConstraintInternal(MIN_CONSTRAINT, min)
    }

    /**
     * @return Returns the inList.
     */
    @Override
    @SuppressWarnings("rawtypes")
    List getInList() {
        InListConstraint c = (InListConstraint)appliedConstraints.get(IN_LIST_CONSTRAINT)
        return c == null ? null : c.getList()
    }

    /**
     * @param inList The inList to set.
     */
    @SuppressWarnings("rawtypes")
    void setInList(List inList) {
        Constraint c = appliedConstraints.get(IN_LIST_CONSTRAINT)
        if (inList == null) {
            appliedConstraints.remove(IN_LIST_CONSTRAINT)
        }
        else {
            applyConstraintInternal(IN_LIST_CONSTRAINT, inList)
        }
    }

    /**
     * @return Returns the range.
     */
    @Override
    @SuppressWarnings("rawtypes")
    Range getRange() {
        RangeConstraint c = (RangeConstraint)appliedConstraints.get(RANGE_CONSTRAINT)
        return c == null ? null : c.getRange()
    }

    /**
     * @param range The range to set.
     */
    @SuppressWarnings("rawtypes")
    void setRange(Range range) {
        if (appliedConstraints.containsKey(MAX_CONSTRAINT)) {
            log.warn("Setting range constraint on property ["+propertyName+"] of class ["+ this.owningClass +"] forced removal of max constraint")
            appliedConstraints.remove(MAX_CONSTRAINT)
        }
        if (appliedConstraints.containsKey(MIN_CONSTRAINT)) {
            log.warn("Setting range constraint on property ["+propertyName+"] of class ["+ this.owningClass +"] forced removal of min constraint")
            appliedConstraints.remove(MIN_CONSTRAINT)
        }
        if (range == null) {
            appliedConstraints.remove(RANGE_CONSTRAINT)
        }
        else {
            applyConstraintInternal(RANGE_CONSTRAINT, range)
        }
    }

    /**
     * @return The scale, if defined for this property; null, otherwise
     */
    @Override
    Integer getScale() {
        ScaleConstraint scaleConstraint = (ScaleConstraint)appliedConstraints.get(SCALE_CONSTRAINT)
        return scaleConstraint == null ? null : scaleConstraint.getScale()
    }

    /**
     * @return Returns the size.
     */
    @Override
    @SuppressWarnings("rawtypes")
    Range getSize() {
        SizeConstraint c = (SizeConstraint)appliedConstraints.get(SIZE_CONSTRAINT)
        return c == null ? null : c.getRange()
    }

    /**
     * @param size The size to set.
     */
    @SuppressWarnings("rawtypes")
    void setSize(Range size) {
        Constraint c = appliedConstraints.get(SIZE_CONSTRAINT)
        if (size == null) {
            appliedConstraints.remove(SIZE_CONSTRAINT)
        }
        else {
            applyConstraintInternal(SIZE_CONSTRAINT, size)
        }
    }

    /**
     * @return the blank.
     */
    @Override
    boolean isBlank() {
        Object cons = appliedConstraints.get(BLANK_CONSTRAINT)
        return cons == null || (Boolean)((BlankConstraint)cons).getParameter()
    }

    /**
     * @param blank The blank to set.
     */
    void setBlank(boolean blank) {
        if (isNotValidStringType()) {
            throw new MissingPropertyException("Blank constraint can only be applied to a String property",
                    BLANK_CONSTRAINT, this.owningClass)
        }

        if (!blank) {
            applyConstraintInternal(BLANK_CONSTRAINT, blank)
        }
        else {
            appliedConstraints.remove(BLANK_CONSTRAINT)
        }
    }

    /**
     * @return Returns the email.
     */
    @Override
    boolean isEmail() {
        if (isNotValidStringType()) {
            throw new MissingPropertyException("Email constraint only applies to a String property",
                    EMAIL_CONSTRAINT, this.owningClass)
        }

        return appliedConstraints.containsKey(EMAIL_CONSTRAINT)
    }

    /**
     * @param email The email to set.
     */
    void setEmail(boolean email) {
        if (isNotValidStringType()) {
            throw new MissingPropertyException("Email constraint can only be applied to a String property",
                    EMAIL_CONSTRAINT, this.owningClass)
        }

        Constraint c = appliedConstraints.get(EMAIL_CONSTRAINT)
        if (email) {
            applyConstraintInternal(EMAIL_CONSTRAINT, email)
        }
        else {
            if (c != null) {
                appliedConstraints.remove(EMAIL_CONSTRAINT)
            }
        }
    }

    private boolean isNotValidStringType() {
        return !CharSequence.class.isAssignableFrom(propertyType)
    }

    /**
     * @return Returns the creditCard.
     */
    @Override
    boolean isCreditCard() {
        if (isNotValidStringType()) {
            throw new MissingPropertyException("CreditCard constraint only applies to a String property",
                    CREDIT_CARD_CONSTRAINT, this.owningClass)
        }

        return appliedConstraints.containsKey(CREDIT_CARD_CONSTRAINT)
    }

    /**
     * @param creditCard The creditCard to set.
     */
    void setCreditCard(boolean creditCard) {
        if (isNotValidStringType()) {
            throw new MissingPropertyException("CreditCard constraint only applies to a String property",
                    CREDIT_CARD_CONSTRAINT, this.owningClass)
        }

        Constraint c = appliedConstraints.get(CREDIT_CARD_CONSTRAINT)
        if (creditCard) {
            applyConstraintInternal(CREDIT_CARD_CONSTRAINT, creditCard)
        }
        else {
            if (c != null) {
                appliedConstraints.remove(CREDIT_CARD_CONSTRAINT)
            }
        }
    }

    /**
     * @return Returns the matches.
     */
    @Override
    String getMatches() {
        if (isNotValidStringType()) {
            throw new MissingPropertyException("Matches constraint only applies to a String property",
                    MATCHES_CONSTRAINT, this.owningClass)
        }
        MatchesConstraint c = (MatchesConstraint)appliedConstraints.get(MATCHES_CONSTRAINT)
        return c == null ? null : c.regex
    }

    /**
     * @param regex The matches to set.
     */
    void setMatches(String regex) {
        if (isNotValidStringType()) {
            throw new MissingPropertyException("Matches constraint can only be applied to a String property",
                    MATCHES_CONSTRAINT, this.owningClass)
        }

        if (regex == null) {
            appliedConstraints.remove(MATCHES_CONSTRAINT)
        }
        else {
            applyConstraintInternal(MATCHES_CONSTRAINT, regex)
        }
    }

    /**
     * @return Returns the notEqual.
     */
    @Override
    Object getNotEqual() {
        NotEqualConstraint c = (NotEqualConstraint)appliedConstraints.get(NOT_EQUAL_CONSTRAINT)
        return c == null ? null : c.getNotEqualTo()
    }

    /**
     * @return Returns the maxSize.
     */
    @Override
    Integer getMaxSize() {
        Integer maxSize = null

        MaxSizeConstraint maxSizeConstraint = (MaxSizeConstraint)appliedConstraints.get(MAX_SIZE_CONSTRAINT)
        SizeConstraint sizeConstraint = (SizeConstraint)appliedConstraints.get(SIZE_CONSTRAINT)

        if (maxSizeConstraint != null || sizeConstraint != null) {
            int maxSizeConstraintValue = maxSizeConstraint == null ? Integer.MAX_VALUE : maxSizeConstraint.getMaxSize()
            int sizeConstraintHighValue = sizeConstraint == null ? Integer.MAX_VALUE : sizeConstraint.getRange().getToInt()
            maxSize = Math.min(maxSizeConstraintValue, sizeConstraintHighValue)
        }

        return maxSize
    }

    /**
     * @param maxSize The maxSize to set.
     */
    void setMaxSize(Integer maxSize) {
        applyConstraintInternal(MAX_SIZE_CONSTRAINT, maxSize)
    }

    /**
     * @return Returns the minSize.
     */
    @Override
    Integer getMinSize() {
        Integer minSize = null

        MinSizeConstraint minSizeConstraint = (MinSizeConstraint)appliedConstraints.get(MIN_SIZE_CONSTRAINT)
        SizeConstraint sizeConstraint = (SizeConstraint)appliedConstraints.get(SIZE_CONSTRAINT)

        if (minSizeConstraint != null || sizeConstraint != null) {
            int minSizeConstraintValue = minSizeConstraint == null ? Integer.MIN_VALUE : minSizeConstraint.getMinSize()
            int sizeConstraintLowValue = sizeConstraint == null ? Integer.MIN_VALUE : sizeConstraint.getRange().getFromInt()

            minSize = Math.max(minSizeConstraintValue, sizeConstraintLowValue)
        }

        return minSize
    }

    /**
     * @param minSize The minLength to set.
     */
    void setMinSize(Integer minSize) {
        applyConstraintInternal(MIN_SIZE_CONSTRAINT, minSize)
    }

    /**
     * @param notEqual The notEqual to set.
     */
    void setNotEqual(Object notEqual) {
        if (notEqual == null) {
            appliedConstraints.remove(NOT_EQUAL_CONSTRAINT)
        }
        else {
            applyConstraintInternal(NOT_EQUAL_CONSTRAINT, notEqual)
        }
    }

    /**
     * @return Returns the nullable.
     */
    @Override
    boolean isNullable() {
        if (appliedConstraints.containsKey(NULLABLE_CONSTRAINT)) {
            NullableConstraint nc = (NullableConstraint)appliedConstraints.get(NULLABLE_CONSTRAINT)
            return nc.isNullable()
        }
        return false
    }

    /**
     * @param nullable The nullable to set.
     */
    void setNullable(boolean nullable) {
        applyConstraintInternal(NULLABLE_CONSTRAINT, nullable)
    }

    /**
     * @return Returns the url.
     */
    @Override
    boolean isUrl() {
        if (isNotValidStringType()) {
            throw new MissingPropertyException("URL constraint can only be applied to a String property",
                    URL_CONSTRAINT, this.owningClass)
        }
        return appliedConstraints.containsKey(URL_CONSTRAINT)
    }


    /**
     * @param url The url to set.
     */
    void setUrl(boolean url) {
        if (isNotValidStringType()) {
            throw new MissingPropertyException("URL constraint can only be applied to a String property",URL_CONSTRAINT, this.owningClass)
        }

        Constraint c = appliedConstraints.get(URL_CONSTRAINT)
        if (url) {
            applyConstraintInternal(URL_CONSTRAINT, url)
        }
        else {
            if (c != null) {
                appliedConstraints.remove(URL_CONSTRAINT)
            }
        }
    }

    @SuppressWarnings("rawtypes")
    Map getAttributes() {
        return attributes
    }


    @SuppressWarnings("rawtypes")
    void setAttributes(Map attributes) {
        this.attributes = attributes
    }
    /**
     * Validate this constrainted property against specified property value
     *
     * @param target The target object to validate
     * @param propertyValue The value of the property to validate
     * @param errors The Errors instances to report errors to
     */
    void validate(Object target, Object propertyValue, Errors errors) {
        List<Constraint> delayedConstraints = new ArrayList<Constraint>()

        // validate only vetoing constraints first, putting non-vetoing into delayedConstraints
        for (Constraint c in appliedConstraints.values()) {
            if (c instanceof VetoingConstraint) {
                // stop validation process when constraint vetoes
                if (((VetoingConstraint)c).validateWithVetoing(target, propertyValue, errors)) {
                    return
                }
            }
            else {
                delayedConstraints.add(c)
            }
        }

        // process non-vetoing constraints
        for (Constraint c : delayedConstraints) {
            c.validate(target, propertyValue, errors)
        }
    }

    /**
     * Checks with this ConstraintedProperty instance supports applying the specified constraint.
     *
     * @param constraintName The name of the constraint
     * @return true if the constraint is supported
     */
    @Override
    boolean supportsContraint(String constraintName) {

        List<ConstraintFactory> constraintFactories = constraintRegistry.findConstraintFactories(constraintName)
        if (constraintFactories.isEmpty()) {
            return PROPERTY_FETCHER.getPropertyDescriptor(constraintName)?.getWriteMethod() != null
        }

        try {

            for(ConstraintFactory cf in constraintFactories) {
                if(cf.supports(propertyType)) {
                    return true
                }
            }
        }
        catch (Exception e) {
            log.error("Exception thrown instantiating constraint [$constraintName] to class [$owningClass]", e)
            throw new ValidationConfigurationException("Exception thrown instantiating constraint [$constraintName] to class [$owningClass]: ${e.message}", e)
        }
    }

    /**
     * Applies a constraint for the specified name and consraint value.
     *
     * @param constraintName The name of the constraint
     * @param constrainingValue The constraining value
     *
     * @throws ValidationConfigurationException Thrown when the specified constraint is not supported by this ConstrainedProperty. Use <code>supportsContraint(String constraintName)</code> to check before calling
     */
    @Override
    void applyConstraint(String constraintName, Object constrainingValue) {
        List<ConstraintFactory> constraintFactories = constraintRegistry.findConstraintFactories(constraintName)

        if (!constraintFactories.isEmpty()) {
            if (constrainingValue == null) {
                appliedConstraints.remove(constraintName)
            }
            else {
                try {
                    Constraint c = instantiateConstraint(constraintName, constrainingValue, true)
                    if (c != null) {
                        appliedConstraints.put(constraintName, c)
                    }
                }
                catch (Exception e) {
                    throw new ValidationConfigurationException("Exception thrown applying constraint [$constraintName] to class [$owningClass] for value [$constrainingValue]: ${e.message}", e)
                }
            }
        }
        else if (hasProperty(constraintName)) {
            ((GroovyObject)this).setProperty(constraintName, constrainingValue)
        }
        else {
            throw new ValidationConfigurationException("Constraint [$constraintName] is not supported for property [$propertyName] of class [$owningClass] with type [$propertyType]")
        }
    }

    protected void applyConstraintInternal(String constraintName, Object constrainingValue) {
        Constraint c = appliedConstraints.get(constraintName)
        if (c == null) {
            for (factory in constraintRegistry.findConstraintFactories(constraintName)) {
                c = factory.build(owningClass, propertyName, constrainingValue)
                if (c != null && c.supports(propertyType)) {
                    appliedConstraints.put(constraintName, c)
                    break
                }
            }
        }
    }

    @Override
    Class getOwner() {
        return this.owningClass
    }

    private Constraint instantiateConstraint(String constraintName, Object constraintValue, boolean validate) throws InstantiationException, IllegalAccessException {
        List<ConstraintFactory> candidateConstraints = constraintRegistry.findConstraintFactories(constraintName)

        for (ConstraintFactory constraintFactory in candidateConstraints) {

            Constraint c = constraintFactory.build(owningClass, propertyName, constraintValue)

            if (validate && c.isValid()) {
                return c
            }
            if (!validate) {
                return c
            }

        }
        return null
    }

    /**
     * Adds a meta constraints which is a non-validating informational constraint.
     *
     * @param name The name of the constraint
     * @param value The value
     */
    void addMetaConstraint(String name, Object value) {
        metaConstraints.put(name, value)
    }

    /**
     * Obtains the value of the named meta constraint.
     * @param name The name of the constraint
     * @return The value
     */
    Object getMetaConstraintValue(String name) {
        return metaConstraints.get(name)
    }

    /**
     * @return Returns the metaConstraints.
     */
    Map<String, Object> getMetaConstraints() {
        return metaConstraints
    }
}

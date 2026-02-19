package grails.orm;

import groovy.lang.MissingMethodException;

/**
 * Enum representing the supported methods in HibernateCriteriaBuilder.
 */
public enum CriteriaMethods {
    AND("and"),
    IS_NULL("isNull"),
    IS_NOT_NULL("isNotNull"),
    NOT("not"),
    OR("or"),
    ID_EQUALS("idEq"),
    IS_EMPTY("isEmpty"),
    IS_NOT_EMPTY("isNotEmpty"),
    RLIKE("rlike"),
    BETWEEN("between"),
    EQUALS("eq"),
    EQUALS_PROPERTY("eqProperty"),
    GREATER_THAN("gt"),
    GREATER_THAN_PROPERTY("gtProperty"),
    GREATER_THAN_OR_EQUAL("ge"),
    GREATER_THAN_OR_EQUAL_PROPERTY("geProperty"),
    ILIKE("ilike"),
    IN("in"),
    LESS_THAN("lt"),
    LESS_THAN_PROPERTY("ltProperty"),
    LESS_THAN_OR_EQUAL("le"),
    LESS_THAN_OR_EQUAL_PROPERTY("leProperty"),
    LIKE("like"),
    NOT_EQUAL("ne"),
    NOT_EQUAL_PROPERTY("neProperty"),
    SIZE_EQUALS("sizeEq"),
    ORDER_DESCENDING("desc"),
    ORDER_ASCENDING("asc"),
    ROOT_DO_CALL("doCall"),
    ROOT_CALL("call"),
    LIST_CALL("list"),
    LIST_DISTINCT_CALL("listDistinct"),
    COUNT_CALL("count"),
    GET_CALL("get"),
    SCROLL_CALL("scroll"),
    PROJECTIONS("projections");

    private final String name;

    CriteriaMethods(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Factory method to convert a string method name to a CriteriaMethods enum.
     * @param name The method name
     * @param targetClass The class where the method was invoked (for exception reporting)
     * @param args The arguments passed to the method (for exception reporting)
     * @return The corresponding CriteriaMethods enum
     * @throws MissingMethodException if the method name is not recognized
     */
    public static CriteriaMethods fromName(String name, Class<?> targetClass, Object[] args) {
        for (CriteriaMethods m : values()) {
            if (m.name.equals(name)) {
                return m;
            }
        }
        throw new MissingMethodException(name, targetClass, args);
    }

    /**
     * Internal factory method to convert a string method name to a CriteriaMethods enum without throwing an exception.
     * Useful for logic that checks if a method is a known criteria method before deciding how to handle it.
     * @param name The method name
     * @return The corresponding CriteriaMethods enum or null if not found
     */
    public static CriteriaMethods fromName(String name) {
        for (CriteriaMethods m : values()) {
            if (m.name.equals(name)) {
                return m;
            }
        }
        return null;
    }
}

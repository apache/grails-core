package org.grails.orm.hibernate.cfg.domainbinding;

import groovy.transform.CompileStatic;


public enum CascadeBehavior {

    /**
     * Cascades all operations, including delete-orphan. Maps to "all".
     */
    ALL("all"),

    /**
     * Cascades save and update operations. Maps to "save-update".
     */
    SAVE_UPDATE("save-update"),

    /**
     * Cascades the merge operation. Maps to "merge".
     */
    MERGE("merge"),

    /**
     * Cascades the delete operation. Maps to "delete".
     */
    DELETE("delete"),

    /**
     * Cascades the lock operation. Maps to "lock".
     */
    LOCK("lock"),

    /**
     * Cascades the replicate operation. Maps to "replicate".
     */
    REPLICATE("replicate"),

    /**
     * Cascades the evict (detach) operation. Maps to "evict".
     */
    EVICT("evict"),

    /**
     * Cascades the persist operation. Maps to "persist".
     */
    PERSIST("persist"),

    /**
     * No operations are cascaded. This is the default for unrecognized values.
     */
    NONE("none");

    private final String value;

    CascadeBehavior(String value) {
        this.value = value;
    }

    /**
     * @return The string representation of the cascade behavior used in the mapping block.
     */
    public String getValue() {
        return value;
    }

    /**
     * Safely converts a string from a mapping configuration to a {@link CascadeBehavior} enum.
     * <p>
     * This method is case-insensitive and will return {@link #NONE} if the input string
     * is null or does not match any known cascade behavior.
     *
     * @param value The string to parse (e.g., "all", "save-update").
     * @return The corresponding {@link CascadeBehavior}, or {@link #NONE} if not found.
     */
    public static CascadeBehavior fromString(String value) {
        if (value == null) {
            return NONE;
        }
        for (CascadeBehavior behavior : CascadeBehavior.values()) {
            if (behavior.value.equalsIgnoreCase(value)) {
                return behavior;
            }
        }
        // Default to the safest option for any truly unrecognized cascade string.
        return NONE;
    }

}
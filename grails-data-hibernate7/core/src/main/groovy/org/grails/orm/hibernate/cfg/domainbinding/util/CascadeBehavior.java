package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.hibernate.MappingException;

import java.util.Arrays;


public enum CascadeBehavior {

    /**
     * Cascades all operations, including delete-orphan. Maps to "all".
     */
    ALL("all"),

    /**
     * Cascades save and update operations. Maps to "persist,merge".
     */
    SAVE_UPDATE("persist,merge"),

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
     * Cascades all operations, including delete-orphan. Maps to "all-delete-orphan".
     */
    ALL_DELETE_ORPHAN("all-delete-orphan"),

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

    public boolean isSaveUpdate() {
        return this == ALL || this == ALL_DELETE_ORPHAN || this == SAVE_UPDATE;
    }

    /**
     * Check if a save-update cascade is defined within the Hibernate cascade properties string.
     * @param cascade The string containing the cascade properties.
     * @return True if save-update or any other cascade property that encompasses those is present.
     */
    public static boolean isSaveUpdate(String cascade) {
        if (cascade == null || cascade.isEmpty()) {
            return false;
        }

        String[] cascades = cascade.split(",");

        for (String cascadeProp : cascades) {
            String trimmedProp = cascadeProp.trim();
            try {
                if (CascadeBehavior.fromString(trimmedProp).isSaveUpdate()) {
                    return true;
                }
            } catch (MappingException e) {
                // ignore
            }
        }

        return cascade.contains(PERSIST.getValue()) && cascade.contains(MERGE.getValue());
    }


    public static CascadeBehavior fromString(String value) {
        return Arrays.stream(CascadeBehavior.values())
                .filter(behavior -> behavior.value.equalsIgnoreCase(value)
                        || ("save-update".equalsIgnoreCase(value) && behavior == SAVE_UPDATE)
                )
                .findFirst()
                .orElseThrow(() -> new MappingException("Invalid Cascade value: " + value + "."));

    }

}
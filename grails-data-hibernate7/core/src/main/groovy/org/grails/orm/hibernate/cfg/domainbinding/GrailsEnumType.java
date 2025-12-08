package org.grails.orm.hibernate.cfg.domainbinding;

public enum GrailsEnumType
{
    DEFAULT("default"), STRING("string"), ORDINAL("ordinal"), IDENTITY("identity");
    private final String type;

    private GrailsEnumType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}

package org.grails.datastore.gorm.neo4j;


/**
 * combination of relationship type and direction to be used a key in a map
 */
public class TypeDirectionPair {

    private String type;
    private boolean outgoing;
    private String targetType;

    public TypeDirectionPair(String type, boolean outgoing) {
        this.type = type;
        this.outgoing = outgoing;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public void setOutgoing(boolean outgoing) {
        this.outgoing = outgoing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeDirectionPair that = (TypeDirectionPair) o;

        if (outgoing != that.outgoing) return false;
        if (!type.equals(that.type)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (outgoing ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TypeDirectionPair{" +
                "type='" + type + '\'' +
                ", outgoing=" + outgoing +
                '}';
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetType() {
        return targetType;
    }
}

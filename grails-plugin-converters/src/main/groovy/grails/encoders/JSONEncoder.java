package grails.encoders;

import grails.converters.JSON;
import org.grails.encoder.impl.BasicJSONEncoder;

public class JSONEncoder extends BasicJSONEncoder {
    @Override
    protected Object encodeAsJsonObject(Object o) {
        if(o instanceof JSON) {
            return o;
        } else {
            return new JSON(o);
        }
    }
}

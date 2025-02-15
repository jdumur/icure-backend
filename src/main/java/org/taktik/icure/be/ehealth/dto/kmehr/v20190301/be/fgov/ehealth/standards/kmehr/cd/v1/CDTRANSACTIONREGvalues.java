//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2019.06.14 at 03:49:41 PM CEST 
//


package org.taktik.icure.be.ehealth.dto.kmehr.v20190301.be.fgov.ehealth.standards.kmehr.cd.v1;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CD-TRANSACTION-REGvalues.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="CD-TRANSACTION-REGvalues">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="qermid-stent-intervention"/>
 *     &lt;enumeration value="orthopride-intervention"/>
 *     &lt;enumeration value="defib-intervention"/>
 *     &lt;enumeration value="pacemaker-intervention"/>
 *     &lt;enumeration value="arthritis-notification"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "CD-TRANSACTION-REGvalues")
@XmlEnum
public enum CDTRANSACTIONREGvalues {

    @XmlEnumValue("qermid-stent-intervention")
    QERMID_STENT_INTERVENTION("qermid-stent-intervention"),
    @XmlEnumValue("orthopride-intervention")
    ORTHOPRIDE_INTERVENTION("orthopride-intervention"),
    @XmlEnumValue("defib-intervention")
    DEFIB_INTERVENTION("defib-intervention"),
    @XmlEnumValue("pacemaker-intervention")
    PACEMAKER_INTERVENTION("pacemaker-intervention"),
    @XmlEnumValue("arthritis-notification")
    ARTHRITIS_NOTIFICATION("arthritis-notification");
    private final String value;

    CDTRANSACTIONREGvalues(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static CDTRANSACTIONREGvalues fromValue(String v) {
        for (CDTRANSACTIONREGvalues c: CDTRANSACTIONREGvalues.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}

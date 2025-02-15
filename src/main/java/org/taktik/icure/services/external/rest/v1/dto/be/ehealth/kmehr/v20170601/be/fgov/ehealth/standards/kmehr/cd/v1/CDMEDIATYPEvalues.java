//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2019.06.14 at 03:50:11 PM CEST 
//


package org.taktik.icure.services.external.rest.v1.dto.be.ehealth.kmehr.v20170601.be.fgov.ehealth.standards.kmehr.cd.v1;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CD-MEDIATYPEvalues.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="CD-MEDIATYPEvalues">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="application/msword"/>
 *     &lt;enumeration value="application/pdf"/>
 *     &lt;enumeration value="audio/basic"/>
 *     &lt;enumeration value="audio/k32adpcm"/>
 *     &lt;enumeration value="audio/mp3"/>
 *     &lt;enumeration value="image/g3fax"/>
 *     &lt;enumeration value="image/gif"/>
 *     &lt;enumeration value="image/jpeg"/>
 *     &lt;enumeration value="image/png"/>
 *     &lt;enumeration value="image/tiff"/>
 *     &lt;enumeration value="model/vrml"/>
 *     &lt;enumeration value="multipart/x-hl7-cda-level1"/>
 *     &lt;enumeration value="text/html"/>
 *     &lt;enumeration value="text/plain"/>
 *     &lt;enumeration value="text/rtf"/>
 *     &lt;enumeration value="text/sgml"/>
 *     &lt;enumeration value="text/x-hl7-ft"/>
 *     &lt;enumeration value="text/xml"/>
 *     &lt;enumeration value="video/mpeg"/>
 *     &lt;enumeration value="video/x-avi"/>
 *     &lt;enumeration value="kmb/transaction"/>
 *     &lt;enumeration value="image/dicom"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "CD-MEDIATYPEvalues")
@XmlEnum
public enum CDMEDIATYPEvalues {

    @XmlEnumValue("application/msword")
    APPLICATION_MSWORD("application/msword"),
    @XmlEnumValue("application/pdf")
    APPLICATION_PDF("application/pdf"),
    @XmlEnumValue("audio/basic")
    AUDIO_BASIC("audio/basic"),
    @XmlEnumValue("audio/k32adpcm")
    AUDIO_K_32_ADPCM("audio/k32adpcm"),
    @XmlEnumValue("audio/mp3")
    AUDIO_MP_3("audio/mp3"),
    @XmlEnumValue("image/g3fax")
    IMAGE_G_3_FAX("image/g3fax"),
    @XmlEnumValue("image/gif")
    IMAGE_GIF("image/gif"),
    @XmlEnumValue("image/jpeg")
    IMAGE_JPEG("image/jpeg"),
    @XmlEnumValue("image/png")
    IMAGE_PNG("image/png"),
    @XmlEnumValue("image/tiff")
    IMAGE_TIFF("image/tiff"),
    @XmlEnumValue("model/vrml")
    MODEL_VRML("model/vrml"),
    @XmlEnumValue("multipart/x-hl7-cda-level1")
    MULTIPART_X_HL_7_CDA_LEVEL_1("multipart/x-hl7-cda-level1"),
    @XmlEnumValue("text/html")
    TEXT_HTML("text/html"),
    @XmlEnumValue("text/plain")
    TEXT_PLAIN("text/plain"),
    @XmlEnumValue("text/rtf")
    TEXT_RTF("text/rtf"),
    @XmlEnumValue("text/sgml")
    TEXT_SGML("text/sgml"),
    @XmlEnumValue("text/x-hl7-ft")
    TEXT_X_HL_7_FT("text/x-hl7-ft"),
    @XmlEnumValue("text/xml")
    TEXT_XML("text/xml"),
    @XmlEnumValue("video/mpeg")
    VIDEO_MPEG("video/mpeg"),
    @XmlEnumValue("video/x-avi")
    VIDEO_X_AVI("video/x-avi"),
    @XmlEnumValue("kmb/transaction")
    KMB_TRANSACTION("kmb/transaction"),
    @XmlEnumValue("image/dicom")
    IMAGE_DICOM("image/dicom");
    private final String value;

    CDMEDIATYPEvalues(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static CDMEDIATYPEvalues fromValue(String v) {
        for (CDMEDIATYPEvalues c: CDMEDIATYPEvalues.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}

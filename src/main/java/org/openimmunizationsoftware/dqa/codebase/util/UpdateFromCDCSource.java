package org.openimmunizationsoftware.dqa.codebase.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.Codeset;
import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.Codeset.Code;
import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.Codeset.Code.Reference;
import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.Codeset.Code.Reference.LinkTo;
import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.Codeset.Code.UseDate;
import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.ObjectFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class UpdateFromCDCSource
{
  private static final String VACCINATION_VIS_DOC_TYPE_XML = "Vaccination VIS Doc Type.xml";
  private static final String VACCINATION_VIS_VACCINES_XML = "Vaccination VIS Vaccines.xml";
  private static final String VACCINATION_CPT_CODE_XML = "Vaccination CPT Code.xml";
  private static final String VACCINATION_VACCINATION_TRADE_NAME_XML = "Vaccination Trade Name.xml";
  private static final String VACCINE_GROUP_XML = "Vaccine Group.xml";
  private static final String VACCINATION_CVX_CODE_XML = "Vaccination CVX Code.xml";
  private static final String VACCINATION_MANUFACTURER_CODE_XML = "Vaccination Manufacturer Code.xml";
  private static final String VACCINATION_LOT_NUMBER_PATTERN_XML = "Vaccination Lot Number Pattern.xml";
  private static final String VACCINATION_NDC_CODE_UNIT_OF_USE_XML = "Vaccination NDC Code Unit-of-Use.xml";
  private static final String VACCINATION_NDC_CODE_UNIT_OF_SALE_XML = "Vaccination NDC Code Unit-of-Sale.xml";
  private static final String VACCINATION_INJECTION_AMOUNT = "Injection Amount.xml";
  private static final String VACCINATION_INJECTION_GUIDANCE = "Injection Guidance.xml";
  private static final String VACCINATION_INJECTION_GUIDANCE_SITE = "Injection Guidance Site.xml";
  private static final String BODY_ROUTE = "Body Route.xml";
  private static final String BODY_SITE = "Body Site.xml";

  private static final String[] ALL_XML = { VACCINATION_VIS_DOC_TYPE_XML, VACCINATION_VIS_VACCINES_XML,
      VACCINATION_CPT_CODE_XML, VACCINATION_VACCINATION_TRADE_NAME_XML, VACCINE_GROUP_XML, VACCINATION_CVX_CODE_XML,
      VACCINATION_MANUFACTURER_CODE_XML, VACCINATION_NDC_CODE_UNIT_OF_USE_XML, VACCINATION_NDC_CODE_UNIT_OF_SALE_XML,
      VACCINATION_INJECTION_AMOUNT, VACCINATION_INJECTION_GUIDANCE, VACCINATION_INJECTION_GUIDANCE_SITE, BODY_ROUTE,
      BODY_SITE, VACCINATION_LOT_NUMBER_PATTERN_XML };

  private static final String CODE_SET_UNIT_OF_USE_LABEL = "Vaccination NDC for Unit-of-Use";
  private static final String CODE_SET_UNIT_OF_SALE_LABEL = "Vaccination NDC for Unit-of-Sale";
  private static final String CODE_SET_UNIT_OF_USE_TYPE = "VACCINATION_NDC_CODE_UNIT_OF_USE";
  private static final String CODE_SET_UNIT_OF_SALE_TYPE = "VACCINATION_NDC_CODE_UNIT_OF_SALE";
  private static final String CODE_SET_CVX = "VACCINATION_CVX_CODE";
  private static final String CODE_SET_MVX = "VACCINATION_MANUFACTURER_CODE";
  private static final String CODE_SET_VACCINE_GROUP = "VACCINE_GROUP";
  private static final String CODE_SET_VACCINATION_VIS_DOC_TYPE = "VACCINATION_VIS_DOC_TYPE";
  private static final String CODE_SET_VACCINATION_VIS_VACCINES = "VACCINATION_VIS_VACCINES";
  private static final String CODE_SET_LOT_NUMBER_PATTERN = "VACCINATION_LOT_NUMBER_PATTERN";

  public static final String DEFAULT_CODEBASE_LOCATION = "../codebase";

  private File baseLocationFile;
  private File setLocationFile;
  private File cdcSourceLocationFile;
  private File linkerFile;
  private File unitSaleFile;
  private File unitUseFile;
  private File cvxFile;
  private File vac2vgFile;
  private File cptFile;
  private File cvxvisFile;
  private File mvxFile;
  private File tradenameFile;

  public UpdateFromCDCSource(String[] args) throws IOException {
    String baseLocationString = DEFAULT_CODEBASE_LOCATION;
    if (args.length > 0) {
      baseLocationString = args[0];
    }
    baseLocationFile = new File(baseLocationString);
    if (!baseLocationFile.exists()) {
      throw new IllegalArgumentException("Can't open codebase location: " + baseLocationFile.getCanonicalPath());
    }
    setLocationFile = new File(baseLocationFile, "base/sets");
    if (!setLocationFile.exists()) {
      throw new IllegalArgumentException("Can't open code set location: " + setLocationFile.getCanonicalPath());
    }

    cdcSourceLocationFile = new File(baseLocationFile, "cdc-source");
    if (!cdcSourceLocationFile.exists()) {
      throw new IllegalArgumentException("Can't open cdc source location: " + cdcSourceLocationFile.getCanonicalPath());
    }

    linkerFile = new File(cdcSourceLocationFile, "NDC_Linker.txt");
    if (!linkerFile.exists()) {
      throw new IllegalArgumentException("Can't open linker file: " + cdcSourceLocationFile.getCanonicalPath());
    }

    unitSaleFile = new File(cdcSourceLocationFile, "NDC_Unit_sale.txt");
    if (!unitSaleFile.exists()) {
      throw new IllegalArgumentException("Can't open Unit of Sale file: " + unitSaleFile.getCanonicalPath());
    }

    unitUseFile = new File(cdcSourceLocationFile, "NDC_Unit_use.txt");
    if (!unitUseFile.exists()) {
      throw new IllegalArgumentException("Can't open Unit of Use file: " + unitUseFile.getCanonicalPath());
    }

    cvxFile = new File(cdcSourceLocationFile, "cvx.xml");
    if (!cvxFile.exists()) {
      throw new IllegalArgumentException("Can't open CVX file: " + cvxFile.getCanonicalPath());
    }

    vac2vgFile = new File(cdcSourceLocationFile, "vac2vg.xml");
    if (!vac2vgFile.exists()) {
      throw new IllegalArgumentException("Can't open Vac 2 Vaccine Group file: " + vac2vgFile.getCanonicalPath());
    }

    cptFile = new File(cdcSourceLocationFile, "cpt.xml");
    if (!cptFile.exists()) {
      throw new IllegalArgumentException("Can't open CPT file: " + cptFile.getCanonicalPath());
    }

    cvxvisFile = new File(cdcSourceLocationFile, "cvxvis.xml");
    if (!cvxvisFile.exists()) {
      throw new IllegalArgumentException("Can't open CVX VIS file: " + cvxvisFile.getCanonicalPath());
    }

    mvxFile = new File(cdcSourceLocationFile, "mvx.xml");
    if (!mvxFile.exists()) {
      throw new IllegalArgumentException("Can't open MVX file: " + mvxFile.getCanonicalPath());
    }

    tradenameFile = new File(cdcSourceLocationFile, "tradename.xml");
    if (!tradenameFile.exists()) {
      throw new IllegalArgumentException("Can't open Tradename file: " + tradenameFile.getCanonicalPath());
    }
  }

  public static void main(String[] args) throws IOException {
    UpdateFromCDCSource update = new UpdateFromCDCSource(args);
    update.go();
  }

  private class Link implements Comparable<Link>
  {
    public String outerId = "";
    public String innerId = "";
    public String mvx = "";

    @Override
    public int compareTo(Link other) {
      if (outerId.equals(other.outerId)) {
        return innerId.compareTo(other.innerId);
      }
      return outerId.compareTo(other.outerId);
    }
  }

  private Map<String, Code> codeMapOuter = new HashMap<>();
  private Map<String, Code> codeMapInner = new HashMap<>();

  private Map<String, Set<Link>> linkMapSetByOuterId = new HashMap<>();
  private Map<String, Set<Link>> linkMapSetByInnerId = new HashMap<>();

  private Map<String, List<Codeset.Code>> cvxToVaccineGroupListMap = new HashMap<>();

  public void go() throws IOException {
    {
      readLinkFile();

      Codeset codesetUnitOfSale = readCodsetUnitOfSale(codeMapOuter);
      Codeset codesetUnitOfUse = readCodsetUnitOfUse(codeMapInner);

      linkUnitOfSale();
      linkUnitOfUse();

      saveCodeSet(codesetUnitOfSale, VACCINATION_NDC_CODE_UNIT_OF_SALE_XML);
      saveCodeSet(codesetUnitOfUse, VACCINATION_NDC_CODE_UNIT_OF_USE_XML);
    }

    updateVaccineGroup();
    updateCvx();
    updateCpt();
    updateVis();
    updateMvx();
    updateTradename();
    verifyLotNumberPatterns();

    crossLink();
  }

  private void updateCvx() throws IOException {
    System.out.println("CVX");
    ObjectFactory objectFactory = new ObjectFactory();
    String filename = VACCINATION_CVX_CODE_XML;
    Codeset codeset = unmarshalCodeset(filename);
    int countTotal = codeset.getCode().size();
    int countAdded = 0;
    if (codeset != null) {

      try {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
        Document doc = dbBuilder.parse(cvxFile);
        doc.getDocumentElement().normalize();
        NodeList n0List = doc.getElementsByTagName("CVXCodes");
        for (int level0 = 0; level0 < n0List.getLength(); level0++) {
          Node n1Node = n0List.item(level0);
          NodeList n1List = n1Node.getChildNodes();
          for (int level1 = 0; level1 < n1List.getLength(); level1++) {
            Node n2Node = n1List.item(level1);
            if (n2Node.getNodeName().equals("CVXInfo")) {
              NodeList n2List = n2Node.getChildNodes();
              String shortDescription = "";
              String fullVaccinename = "";
              String cvxCode = "";
              String notes = "";
              String status = "";
              Date lastUpdate = null;
              for (int level2 = 0; level2 < n2List.getLength(); level2++) {
                Node n3Node = n2List.item(level2);
                if (n3Node.getNodeName().equals("ShortDescription")) {
                  shortDescription = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("FullVaccinename")) {
                  fullVaccinename = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("CVXCode")) {
                  cvxCode = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("Notes")) {
                  notes = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("Status")) {
                  status = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("LastUpdated")) {
                  lastUpdate = readDate(n3Node);
                }
              }
              if (!cvxCode.equals("")) {
                Codeset.Code c = getOrCreateCode(codeset, cvxCode);
                if (isEmpty(c.getLabel())) {
                  c.setLabel(shortDescription);
                }
                if (isEmpty(c.getDescription())) {
                  c.setDescription(fullVaccinename);
                  if (notes.length() > 0) {
                    c.setDescription(c.getDescription() + " Notes: " + notes);
                  }
                }
                if (c.getCodeStatus() == null) {
                  c.setCodeStatus(new Codeset.Code.CodeStatus());
                }
                if (c.getCodeStatus().getStatus() == null) {
                  c.getCodeStatus().setStatus("Valid");
                }
                if (c.getConceptType() == null) {
                  if (status.equals("Never Active")) {
                    c.setConceptType("never active");
                  } else if (status.equals("Pending")) {
                    c.setConceptType("pending");
                  } else if (status.equals("Non-US")) {
                    c.setConceptType("foreign vaccine");
                  } else {
                    c.setConceptType("vaccine");
                  }
                }

                List<Codeset.Code> codeList = cvxToVaccineGroupListMap.get(cvxCode);
                if (codeList != null && codeList.size() > 0) {
                  Reference reference = getReference(objectFactory, c);
                  for (Codeset.Code code : codeList) {
                    boolean found = false;
                    for (LinkTo linkTo : reference.getLinkTo()) {
                      if (linkTo.getCodeset().equals(CODE_SET_VACCINE_GROUP)
                          && linkTo.getValue().equals(code.getValue())) {
                        found = true;
                      }
                    }
                    if (!found) {
                      LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
                      reference.getLinkTo().add(linkTo);
                      linkTo.setCodeset(CODE_SET_VACCINE_GROUP);
                      linkTo.setValue(code.getValue());
                    }
                  }
                }

                setUseDateBasedOnStatus(objectFactory, status, lastUpdate, c);
              }

            }
          }
        }
      } catch (ParserConfigurationException | SAXException e) {
        e.printStackTrace();
      }

      marshalCodeset(codeset, filename);
    }
    countAdded = codeset.getCode().size() - countTotal;
    countTotal = codeset.getCode().size();
    System.out.println("  + Added:   " + countAdded);
    System.out.println("  + Total:   " + countTotal);
  }

  private void updateMvx() throws IOException {
    System.out.println("MVX");
    ObjectFactory objectFactory = new ObjectFactory();
    String filename = VACCINATION_MANUFACTURER_CODE_XML;
    Codeset codeset = unmarshalCodeset(filename);
    int countTotal = codeset.getCode().size();
    int countAdded = 0;
    boolean updated = false;
    if (codeset != null) {
      try {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
        Document doc = dbBuilder.parse(mvxFile);
        doc.getDocumentElement().normalize();
        NodeList n0List = doc.getElementsByTagName("MVXCodes");
        for (int level0 = 0; level0 < n0List.getLength(); level0++) {
          Node n1Node = n0List.item(level0);
          NodeList n1List = n1Node.getChildNodes();
          for (int level1 = 0; level1 < n1List.getLength(); level1++) {
            Node n2Node = n1List.item(level1);
            if (n2Node.getNodeName().equals("MVXInfo")) {
              NodeList n2List = n2Node.getChildNodes();
              String mvxCode = "";
              String manufacturerName = "";
              String notes = "";
              String status = "";
              Date lastUpdate = null;
              for (int level2 = 0; level2 < n2List.getLength(); level2++) {
                Node n3Node = n2List.item(level2);
                if (n3Node.getNodeName().equals("ManufacturerName")) {
                  manufacturerName = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("MVX_CODE")) {
                  mvxCode = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("Notes")) {
                  notes = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("Status")) {
                  status = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("LastUpdated")) {
                  lastUpdate = readDate(n3Node);
                }
              }
              if (!mvxCode.equals("")) {
                Codeset.Code c = getOrCreateCode(codeset, mvxCode);
                if (isEmpty(c.getLabel())) {
                  c.setLabel(manufacturerName);
                  updated = true;
                }
                if (isEmpty(c.getDescription())) {
                  c.setDescription(notes);
                  updated = true;
                }
                if (c.getCodeStatus() == null) {
                  c.setCodeStatus(new Codeset.Code.CodeStatus());
                }
                if (c.getCodeStatus().getStatus() == null) {
                  c.getCodeStatus().setStatus("Valid");
                  updated = true;
                }
                if (setUseDateBasedOnStatus(objectFactory, status, lastUpdate, c)) {
                  updated = true;
                }
              }
            }
          }
        }
      } catch (ParserConfigurationException | SAXException e) {
        e.printStackTrace();
      }

      if (updated || codeset.getCode().size() > countTotal) {
        marshalCodeset(codeset, filename);
      }

    }
    countAdded = codeset.getCode().size() - countTotal;
    countTotal = codeset.getCode().size();
    System.out.println("  + Added:   " + countAdded);
    System.out.println("  + Total:   " + countTotal);
  }

  private boolean setUseDateBasedOnStatus(ObjectFactory objectFactory, String status, Date lastUpdate, Codeset.Code c) {
    if (lastUpdate != null && (status.equals("Inactive") || status.equals("Active"))) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
      UseDate useDate = getUseDate(objectFactory, c);
      if (status.equals("Inactive") && useDate.getNotAfter() == null) {
        if (isEmpty(useDate.getNotAfter())) {
          useDate.setNotAfter(sdf.format(lastUpdate));
          return true;
        }
      } else if (status.equals("Active") && useDate.getNotBefore() == null) {
        if (isEmpty(useDate.getNotBefore())) {
          useDate.setNotBefore(sdf.format(lastUpdate));
          return true;
        }
      }
    }
    return false;
  }

  private Date readDate(Node n3Node) {
    return readDate(clean(n3Node.getTextContent().trim()));
  }

  private Date readDate(String value) {
    if (isEmpty(value)) {
      return null;
    }
    SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy");
    Date date = null;
    try {
      date = sdf.parse(value);
    } catch (DOMException | ParseException e) {
      e.printStackTrace();
    }
    return date;
  }

  private void updateVaccineGroup() throws IOException {
    System.out.println("Vaccine Group");
    String filename = VACCINE_GROUP_XML;
    Codeset codeset = unmarshalCodeset(filename);
    int countTotal = codeset.getCode().size();
    int countAdded = 0;
    boolean updated = false;
    if (codeset != null) {
      try {
        ObjectFactory objectFactory = new ObjectFactory();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
        Document doc = dbBuilder.parse(vac2vgFile);
        doc.getDocumentElement().normalize();
        NodeList n0List = doc.getElementsByTagName("VGCodes");
        for (int level0 = 0; level0 < n0List.getLength(); level0++) {
          Node n1Node = n0List.item(level0);
          NodeList n1List = n1Node.getChildNodes();
          for (int level1 = 0; level1 < n1List.getLength(); level1++) {
            Node n2Node = n1List.item(level1);
            if (n2Node.getNodeName().equals("CVXVGInfo")) {
              NodeList n2List = n2Node.getChildNodes();
              Map<String, String> nameValueMap = readNameValueMap(n2List);
              String shortDescription = nameValueMap.get("ShortDescription");
              String cvxCode = nameValueMap.get("CVXCode");
              // String status = nameValueMap.get("Status");
              String vaccineGroupName = nameValueMap.get("Vaccine Group Name");
              String cvxForVaccineGroup = nameValueMap.get("CVX for Vaccine Group");
              Codeset.Code c = getOrCreateCode(codeset, vaccineGroupName);

              if (isEmpty(c.getLabel())) {
                c.setLabel(shortDescription);
                updated = true;
              }

              Reference reference = getReference(objectFactory, c);
              boolean found = false;
              for (LinkTo linkTo : reference.getLinkTo()) {
                if (linkTo.getCodeset().equals(CODE_SET_CVX) && linkTo.getValue().equals(cvxForVaccineGroup)) {
                  found = true;
                }
              }
              if (!found) {
                LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
                reference.getLinkTo().add(linkTo);
                linkTo.setCodeset(CODE_SET_CVX);
                linkTo.setValue(cvxForVaccineGroup);
                updated = true;
              }

              List<Codeset.Code> codeList = cvxToVaccineGroupListMap.get(cvxCode);
              if (codeList == null) {
                codeList = new ArrayList<>();
                cvxToVaccineGroupListMap.put(cvxCode, codeList);
              }
              codeList.add(c);
            }
          }
        }

      } catch (ParserConfigurationException | SAXException e) {
        e.printStackTrace();
      }
      if (updated || codeset.getCode().size() > countTotal) {
        marshalCodeset(codeset, filename);
      }
      countAdded = codeset.getCode().size() - countTotal;
      countTotal = codeset.getCode().size();
      System.out.println("  + Added:   " + countAdded);
      System.out.println("  + Total:   " + countTotal);
    }
  }

  private void updateCpt() throws IOException {
    System.out.println("CPT");
    String filename = VACCINATION_CPT_CODE_XML;
    Codeset codeset = unmarshalCodeset(filename);
    int countTotal = codeset.getCode().size();
    int countAdded = 0;
    int countUpdated = 0;
    if (codeset != null) {
      try {
        ObjectFactory objectFactory = new ObjectFactory();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
        Document doc = dbBuilder.parse(cptFile);
        doc.getDocumentElement().normalize();
        NodeList n0List = doc.getElementsByTagName("CPTCodes");
        for (int level0 = 0; level0 < n0List.getLength(); level0++) {
          Node n1Node = n0List.item(level0);
          NodeList n1List = n1Node.getChildNodes();
          for (int level1 = 0; level1 < n1List.getLength(); level1++) {
            Node n2Node = n1List.item(level1);
            if (n2Node.getNodeName().equals("CPTInfo")) {
              boolean updated = false;
              NodeList n2List = n2Node.getChildNodes();
              Map<String, String> nameValueMap = readNameValueMap(n2List);
              String cptCode = nameValueMap.get("CPT Code");
              String cptDesc = nameValueMap.get("CPT Desc");
              // String status = nameValueMap.get("Status");
              String comments = nameValueMap.get("Comments");
              // String vaccineName = nameValueMap.get("Vaccine
              // Name");
              String cvxCode = nameValueMap.get("CVX Code");
              // String lastUpdated =
              // nameValueMap.get("LastUpdated");
              Codeset.Code c = getOrCreateCode(codeset, cptCode);

              if (isEmpty(c.getLabel())) {
                c.setLabel(cptDesc);
              }
              if (isEmpty(c.getDescription())) {
                c.setDescription(comments);
                updated = true;
              }
              if (c.getCodeStatus() == null) {
                c.setCodeStatus(objectFactory.createCodesetCodeCodeStatus());
                updated = true;
              }
              if (isEmpty(c.getCodeStatus().getStatus())) {
                c.getCodeStatus().setStatus("Valid");
                updated = true;
              }

              updated = updated || setUniqueLink(objectFactory, cvxCode, c, CODE_SET_CVX);
              if (updated) {
                countUpdated++;
              }
            }
          }
        }

      } catch (ParserConfigurationException | SAXException e) {
        e.printStackTrace();
      }

      countAdded = codeset.getCode().size() - countTotal;
      countTotal = codeset.getCode().size();
      if (countAdded > 0 || countUpdated > 0) {
        marshalCodeset(codeset, filename);
      }
      System.out.println("  + Added:   " + countAdded);
      System.out.println("  + Updated: " + countUpdated);
      System.out.println("  + Total:   " + countTotal);
    }
  }

  private void updateTradename() throws IOException {
    System.out.println("Tradename");
    String filename = VACCINATION_VACCINATION_TRADE_NAME_XML;
    Codeset codeset = unmarshalCodeset(filename);
    int countTotal = codeset.getCode().size();
    int countAdded = 0;
    boolean updated = false;
    if (codeset != null) {
      try {
        ObjectFactory objectFactory = new ObjectFactory();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
        Document doc = dbBuilder.parse(tradenameFile);
        doc.getDocumentElement().normalize();
        NodeList n0List = doc.getElementsByTagName("productnames");
        for (int level0 = 0; level0 < n0List.getLength(); level0++) {
          Node n1Node = n0List.item(level0);
          NodeList n1List = n1Node.getChildNodes();
          for (int level1 = 0; level1 < n1List.getLength(); level1++) {
            Node n2Node = n1List.item(level1);
            if (n2Node.getNodeName().equals("prodInfo")) {
              NodeList n2List = n2Node.getChildNodes();
              Map<String, String> nameValueMap = readNameValueMap(n2List);
              String cdcProductName = nameValueMap.get("CDC Product Name");
              String shortDescription = nameValueMap.get("Short Description");
              String cvxCode = nameValueMap.get("CVXCode");
              // String manufacturer =
              // nameValueMap.get("Manufacturer");
              String mvxCode = nameValueMap.get("MVX Code");
              // String mvxStatus = nameValueMap.get("MVX
              // Status");
              String productNameStatus = nameValueMap.get("Product name Status");
              Date lastUpdated = readDate(nameValueMap.get("Last Updated"));
              Codeset.Code c = getOrCreateCode(codeset, cdcProductName);
              if (isEmpty(c.getLabel())) {
                c.setLabel(shortDescription);
                updated = true;
              }
              if (c.getCodeStatus() == null) {
                c.setCodeStatus(objectFactory.createCodesetCodeCodeStatus());
              }
              if (isEmpty(c.getCodeStatus().getStatus())) {
                c.getCodeStatus().setStatus("Valid");
                updated = true;
              }
              if (setUniqueLink(objectFactory, cvxCode, c, CODE_SET_CVX)) {
                updated = true;
              }
              if (setUniqueLink(objectFactory, mvxCode, c, CODE_SET_MVX)) {
                updated = true;
              }
              if (setUseDateBasedOnStatus(objectFactory, productNameStatus, lastUpdated, c)) {
                updated = true;
              }
            }
          }
        }

      } catch (ParserConfigurationException | SAXException e) {
        e.printStackTrace();
      }
      if (updated || codeset.getCode().size() > countTotal) {
        marshalCodeset(codeset, filename);
      }
      countAdded = codeset.getCode().size() - countTotal;
      countTotal = codeset.getCode().size();
      System.out.println("  + Added:   " + countAdded);
      System.out.println("  + Total:   " + countTotal);
    }
  }

  private void crossLink() {
    System.out.println("Cross Linking the following:");
    Map<String, String> codesestFilenameMap = new HashMap<>();
    Map<String, Codeset> codeSetMap = new HashMap<>();
    Map<String, Map<String, Code>> codesetCodeMap = new HashMap<>();
    List<Codeset> codesetList = new ArrayList<>();
    Set<Codeset> codesetChanged = new HashSet<>();
    for (String filename : ALL_XML) {
      Codeset codeset = unmarshalCodeset(filename);
      codesetList.add(codeset);
      Map<String, Code> codeMap = new HashMap<>();
      codeSetMap.put(codeset.getType(), codeset);
      codesetCodeMap.put(codeset.getType(), codeMap);
      for (Code code : codeset.getCode()) {
        codeMap.put(code.getValue(), code);
      }
      codesestFilenameMap.put(codeset.getType(), filename);
      codeset.getType();
      System.out.println("  + " + codeset.getType());
    }
    for (Codeset codeset : codesetList) {
      System.out.println("Linking out from: " + codeset.getType());
      int linksExamined = 0;
      int linksMade = 0;
      int linksRemoved = 0;
      for (Code code : codeset.getCode()) {
        if (code.getReference() != null) {
          Set<String> alreadySeen = new HashSet<>();
          for (Iterator<LinkTo> linkToIt = code.getReference().getLinkTo().iterator(); linkToIt.hasNext();) {
            LinkTo linkTo = linkToIt.next();
            {
              String alreadySeenKey = linkTo.getCodeset() + "." + linkTo.getValue();
              if (alreadySeen.contains(alreadySeenKey)) {
                System.err.println("  + Link " + alreadySeenKey + " is already mentioned, removing");
                linkToIt.remove();
                linksRemoved++;
                continue;
              }
              alreadySeen.add(alreadySeenKey);
            }
            linksExamined++;
            String otherCodesetType = linkTo.getCodeset();
            Map<String, Code> otherCodeMap = codesetCodeMap.get(otherCodesetType);
            Codeset otherCodeset = codeSetMap.get(otherCodesetType);
            if (otherCodeMap == null) {
              System.err.println("  + Unable to find link to codeset '" + otherCodesetType + "'");
            } else {
              if (linkTo.getValue() == null || linkTo.getValue().equals("")) {
                System.err.println("  + Link has no value in '" + code.getValue() + "'");
              } else {
                Code otherCode = otherCodeMap.get(linkTo.getValue());
                if (otherCode == null) {
                  System.err
                      .println("  + Unable to find link from '" + code.getValue() + "' in codeset '" + codeset.getType()
                          + "' to code '" + linkTo.getValue() + "' in codeset '" + otherCodesetType + "'");
                  linkToIt.remove();
                  linksRemoved++;
                  codesetChanged.add(codeset);
                } else {
                  if (otherCode.getReference() == null) {
                    otherCode.setReference(new Reference());
                  }
                  boolean found = false;
                  String type = codeset.getType();
                  String value = code.getValue();
                  for (LinkTo otherLinkTo : otherCode.getReference().getLinkTo()) {
                    if (otherLinkTo.getCodeset().equals(type) && otherLinkTo.getValue().equals(value)) {
                      found = true;
                      break;
                    }
                  }
                  if (!found) {
                    LinkTo otherLinkTo = new LinkTo();
                    otherLinkTo.setCodeset(type);
                    otherLinkTo.setValue(value);
                    otherCode.getReference().getLinkTo().add(otherLinkTo);
                    linksMade++;
                    codesetChanged.add(otherCodeset);
                  }
                }
              }
            }
          }
        }
      }
      System.out.println("  + Links examined: " + linksExamined);
      System.out.println("  + Links removed:  " + linksRemoved);
      System.out.println("  + Links made:     " + linksMade);

    }
    System.out.println("Saving codesets that have changed");
    for (Codeset codeset : codesetChanged) {
      System.out.println("  + Saving " + codeset.getLabel());
      marshalCodeset(codeset, codesestFilenameMap.get(codeset.getType()));
    }

  }

  private void updateVis() throws IOException {
    System.out.println("VIS");
    String filenameDoc = VACCINATION_VIS_DOC_TYPE_XML;
    String filenameVac = VACCINATION_VIS_VACCINES_XML;
    Codeset codesetDoc = unmarshalCodeset(filenameDoc);
    Codeset codesetVac = unmarshalCodeset(filenameVac);
    int countTotalDoc = codesetDoc.getCode().size();
    int countTotalVac = codesetVac.getCode().size();
    int countAddedDoc = 0;
    int countAddedVac = 0;
    boolean updatedDoc = false;
    boolean updatedVac = false;
    if (codesetDoc != null && codesetVac != null) {
      try {
        ObjectFactory objectFactory = new ObjectFactory();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
        Document doc = dbBuilder.parse(cvxvisFile);
        doc.getDocumentElement().normalize();
        NodeList n0List = doc.getElementsByTagName("CVXVIS");
        for (int level0 = 0; level0 < n0List.getLength(); level0++) {
          Node n1Node = n0List.item(level0);
          NodeList n1List = n1Node.getChildNodes();
          for (int level1 = 0; level1 < n1List.getLength(); level1++) {
            Node n2Node = n1List.item(level1);
            if (n2Node.getNodeName().equals("CVXVISMapping")) {
              NodeList n2List = n2Node.getChildNodes();
              String cvxCode = "";
              // String cvxVaccineDescription = "";
              String fullyEncodedString = "";
              String visDocumentName = "";
              Date visEditionDate = null;
              // String status = "";
              for (int level2 = 0; level2 < n2List.getLength(); level2++) {
                Node n3Node = n2List.item(level2);
                if (n3Node.getNodeName().equals("CVXCode")) {
                  cvxCode = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("CVXVaccineDescription")) {
                  // cvxVaccineDescription =
                  // clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("Fully-encodedString")) {
                  fullyEncodedString = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("VISDocumentName")) {
                  visDocumentName = clean(n3Node.getTextContent().trim());
                } else if (n3Node.getNodeName().equals("VISEditionDate")) {
                  visEditionDate = readDate(n3Node);
                } else if (n3Node.getNodeName().equals("Status")) {
                  // status =
                  // clean(n3Node.getTextContent().trim());
                }
              }
              if (!isEmpty(fullyEncodedString)) {
                Codeset.Code c = getOrCreateCode(codesetDoc, fullyEncodedString);
                if (isEmpty(c.getLabel())) {
                  c.setLabel(visDocumentName);
                  updatedDoc = true;
                }
                if (c.getCodeStatus() == null) {
                  c.setCodeStatus(objectFactory.createCodesetCodeCodeStatus());
                }
                if (isEmpty(c.getCodeStatus().getStatus())) {
                  c.getCodeStatus().setStatus("Valid");
                  updatedDoc = true;
                }
                if (!isEmpty(cvxCode)) {
                  if (setUniqueLink(objectFactory, cvxCode, c, CODE_SET_VACCINATION_VIS_VACCINES)) {
                    updatedDoc = true;
                  }
                }
                if (visEditionDate != null) {
                  if (c.getUseDate() == null || isEmpty(c.getUseDate().getNotBefore())) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                    UseDate useDate = getUseDate(objectFactory, c);
                    useDate.setNotBefore(sdf.format(visEditionDate));
                    updatedDoc = true;
                  }
                }
              }
              if (!isEmpty(cvxCode)) {
                Codeset.Code c = getOrCreateCode(codesetVac, cvxCode);
                if (isEmpty(c.getLabel())) {
                  c.setLabel(visDocumentName);
                  updatedVac = true;
                }
                if (c.getCodeStatus() == null) {
                  c.setCodeStatus(objectFactory.createCodesetCodeCodeStatus());
                }
                if (isEmpty(c.getCodeStatus().getStatus())) {
                  c.getCodeStatus().setStatus("Valid");
                  updatedVac = true;
                }
                if (!isEmpty(fullyEncodedString)) {
                  if (setUniqueLink(objectFactory, fullyEncodedString, c, CODE_SET_VACCINATION_VIS_DOC_TYPE)) {
                    updatedVac = true;
                  }
                }
                if (visEditionDate != null) {
                  if (c.getUseDate() == null || isEmpty(c.getUseDate().getNotBefore())) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                    UseDate useDate = getUseDate(objectFactory, c);
                    useDate.setNotBefore(sdf.format(visEditionDate));
                    updatedVac = true;
                  }
                }
              }
            }
          }
        }

      } catch (ParserConfigurationException | SAXException e) {
        e.printStackTrace();
      }
      if (updatedDoc || codesetDoc.getCode().size() > countTotalDoc) {
        marshalCodeset(codesetDoc, filenameDoc);
      }

      if (updatedVac || codesetVac.getCode().size() > countTotalVac) {
        marshalCodeset(codesetVac, filenameVac);
      }
      countAddedDoc = codesetDoc.getCode().size() - countTotalDoc;
      countTotalDoc = codesetDoc.getCode().size();
      countAddedVac = codesetVac.getCode().size() - countTotalVac;
      countTotalVac = codesetVac.getCode().size();
      System.out.println("  + Added Doc:   " + countAddedDoc);
      System.out.println("  + Total Doc:   " + countTotalDoc);
      System.out.println("  + Added Vac:   " + countAddedVac);
      System.out.println("  + Total Vac:   " + countTotalVac);
    }
  }

  private boolean setUniqueLink(ObjectFactory objectFactory, String value, Codeset.Code c, String codeSetName) {
    if (!isEmpty(value)) {
      Reference reference = getReference(objectFactory, c);
      boolean found = false;
      for (LinkTo linkTo : reference.getLinkTo()) {
        if (linkTo.getCodeset().equals(codeSetName) && linkTo.getValue().equals(value)) {
          found = true;
        }
      }
      if (!found) {
        LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
        reference.getLinkTo().add(linkTo);
        linkTo.setCodeset(codeSetName);
        linkTo.setValue(value);
        return true;
      }
    }
    return false;
  }

  private Map<String, String> readNameValueMap(NodeList n2List) {
    Map<String, String> nameValueMap = new HashMap<>();
    {
      String name = null;
      String value = null;
      for (int level2 = 0; level2 < n2List.getLength(); level2++) {
        Node n3Node = n2List.item(level2);
        if (n3Node.getNodeName().equals("Name")) {
          if (name != null && value != null) {
            nameValueMap.put(name, value);
            value = null;
          }
          name = clean(n3Node.getTextContent().trim());
        } else if (n3Node.getNodeName().equals("Value")) {
          value = clean(n3Node.getTextContent().trim());
        }
      }
      if (name != null && value != null) {
        nameValueMap.put(name, value);
      }
    }
    return nameValueMap;
  }

  private Codeset.Code getOrCreateCode(Codeset codeset, String value) {
    Codeset.Code c = null;
    for (Codeset.Code code : codeset.getCode()) {
      if (code.getValue().equalsIgnoreCase(value)) {
        c = code;
      }
    }
    if (c == null) {
      c = new Codeset.Code();
      c.setValue(value);
      codeset.getCode().add(c);
    }
    return c;
  }

  private static String clean(String s) {
    StringBuffer sb = new StringBuffer();
    char[] c = s.trim().toCharArray();
    boolean whitespace = false;
    for (int i = 0; i < c.length; i++) {
      if (c[i] <= 32) {
        whitespace = true;
      } else {
        if (whitespace) {
          sb.append(' ');
        }
        sb.append(c[i]);
        whitespace = false;
      }
    }
    return sb.toString();
  }

  private Codeset unmarshalCodeset(String filename) {
    Codeset codeset = null;
    try {
      File file = new File(setLocationFile, filename);
      JAXBContext jaxbContext = JAXBContext.newInstance(Codeset.class);
      Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
      codeset = (Codeset) unmarshaller.unmarshal(file);
    } catch (JAXBException e) {
      e.printStackTrace();
    }
    return codeset;
  }

  private void marshalCodeset(Codeset codeset, String filename) {
    Collections.sort(codeset.getCode());
    try {
      File file = new File(setLocationFile, filename);
      JAXBContext jaxbContext = JAXBContext.newInstance(Codeset.class);
      Marshaller marshaller = jaxbContext.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      marshaller.marshal(codeset, file);
    } catch (JAXBException e) {
      e.printStackTrace();
    }
  }

  private void linkUnitOfSale() {
    ObjectFactory objectFactory = new ObjectFactory();
    for (String outerId : linkMapSetByOuterId.keySet()) {
      Code outerCode = codeMapOuter.get(outerId);
      if (outerCode != null) {
        List<Link> linkList = new ArrayList<>(linkMapSetByOuterId.get(outerId));
        Collections.sort(linkList);
        for (Link link : linkList) {
          addMvx(objectFactory, link, outerCode);
          Code innerCode = codeMapInner.get(link.innerId);
          if (innerCode != null) {
            Reference reference = getReference(objectFactory, outerCode);
            boolean needToAdd = true;
            for (LinkTo linkTo : reference.getLinkTo()) {
              if (linkTo.getCodeset().equals(CODE_SET_UNIT_OF_USE_TYPE)
                  && linkTo.getValue().equals(innerCode.getValue())) {
                // don't need to add
                needToAdd = false;
              }
            }
            if (needToAdd) {
              LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
              reference.getLinkTo().add(linkTo);
              linkTo.setCodeset(CODE_SET_UNIT_OF_USE_TYPE);
              linkTo.setValue(innerCode.getValue());
            }
          }
        }
      }
    }
  }

  private void linkUnitOfUse() {
    ObjectFactory objectFactory = new ObjectFactory();
    for (String innerId : linkMapSetByInnerId.keySet()) {
      Code innerCode = codeMapInner.get(innerId);
      if (innerCode != null) {
        List<Link> linkList = new ArrayList<>(linkMapSetByInnerId.get(innerId));
        Collections.sort(linkList);
        for (Link link : linkList) {
          addMvx(objectFactory, link, innerCode);
          Code outerCode = codeMapOuter.get(link.outerId);
          if (outerCode != null) {
            Reference reference = getReference(objectFactory, innerCode);
            boolean needToAdd = true;
            for (LinkTo linkTo : reference.getLinkTo()) {
              if (linkTo.getCodeset().equals(CODE_SET_UNIT_OF_SALE_TYPE)
                  && linkTo.getValue().equals(outerCode.getValue())) {
                // don't need to add
                needToAdd = false;
              }
            }
            if (needToAdd) {
              LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
              reference.getLinkTo().add(linkTo);
              linkTo.setCodeset(CODE_SET_UNIT_OF_SALE_TYPE);
              linkTo.setValue(outerCode.getValue());
            }
          }
        }
      }
    }
  }

  private void addMvx(ObjectFactory objectFactory, Link link, Code code) {
    if (link.mvx.length() > 0) {
      Reference reference = getReference(objectFactory, code);
      for (LinkTo linkTo : reference.getLinkTo()) {
        if (linkTo.getCodeset().equals(CODE_SET_MVX) && linkTo.getValue().equals(link.mvx)) {
          // don't need to add
          return;
        }
      }
      LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
      reference.getLinkTo().add(linkTo);
      linkTo.setCodeset(CODE_SET_MVX);
      linkTo.setValue(link.mvx);
    }
  }

  private void verifyLotNumberPatterns() throws IOException {
    System.out.println("Lot Number: checking reg expressions");
    String filename = VACCINATION_LOT_NUMBER_PATTERN_XML;
    Codeset codeset = unmarshalCodeset(filename);
    int countOkay = 0;
    for (Code code : codeset.getCode()) {
      try {
        Pattern.compile(code.getValue());
        countOkay++;
      } catch (PatternSyntaxException pse) {
        pse.printStackTrace();
        System.err.println("Lot number regular expression was not recognized: " + code.getValue());
      }
    }
    System.out.println("  + Verified: " + countOkay);
    System.out.println("  + Total:    " + codeset.getCode().size());
  }

  private Reference getReference(ObjectFactory objectFactory, Code code) {
    Reference reference = code.getReference();
    if (reference == null) {
      reference = objectFactory.createCodesetCodeReference();
      code.setReference(reference);
    }
    return reference;
  }

  private void saveCodeSet(Codeset codeset, String filename) {
    Collections.sort(codeset.getCode());
    try {
      File file = new File(setLocationFile, filename);
      JAXBContext jaxbContext = JAXBContext.newInstance(Codeset.class);
      Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
      jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      jaxbMarshaller.marshal(codeset, file);
    } catch (JAXBException e) {
      e.printStackTrace();
    }
  }

  private Codeset readCodsetUnitOfSale(Map<String, Code> codeMap) throws FileNotFoundException, IOException {
    System.out.println("Reading Unit of Sale file");
    ObjectFactory objectFactory = new ObjectFactory();
    Codeset codeset = unmarshalCodeset(VACCINATION_NDC_CODE_UNIT_OF_SALE_XML);
    int countTotal = codeset.getCode().size();
    Set<String> codeValuesAlreadyDefined = setupCodeValuesAlreadyDefined(codeset);
    BufferedReader in = new BufferedReader(new FileReader(unitSaleFile));
    String line;
    while ((line = in.readLine()) != null) {
      if (line.length() > UNIT_OF_SALE_NDC11) {
        String[] parts = readAndTrim(line);
        String outerId = parts[UNIT_OF_SALE_NDCOUTERID];
        if (parts.length > UNIT_OF_SALE_NDC11 && !codeMap.containsKey(outerId)) {
          String ndc = parts[UNIT_OF_SALE_NDC11];
          checkNDC(parts, ndc);
          boolean justCreated = false;
          Code code = getOrCreateCode(codeset, ndc);
          if (isEmpty(code.getLabel())) {
            code.setLabel(parts[UNIT_OF_SALE_OUTERPROPNAME]);
            justCreated = true;
          }
          if (isEmpty(code.getDescription())) {
            code.setDescription(parts[UNIT_OF_SALE_OUTERLABELERNAME] + " - " + parts[UNIT_OF_SALE_OUTERGENERICNAME]);
          }
          setCodeStatusAsValid(objectFactory, code);
          String cvxCode = parts[UNIT_OF_SALE_CVX_CODE];
          linkToCvx(objectFactory, code, cvxCode);
          codeMap.put(outerId, code);
          setUseDateNotBefore(parts[UNIT_OF_SALE_OUTERSTARTDATE], code, objectFactory);
          setUseDateNotAfter(parts[UNIT_OF_SALE_OUTERENDDATE], code, objectFactory);
          if (justCreated) {
            String label = parts[UNIT_OF_SALE_OUTERPROPNAME];
            String lastUpdate = parts[UNIT_OF_SALE_LAST_UPDATED_DATE];
            String labeler = parts[UNIT_OF_SALE_OUTERLABELERNAME];
            String generic = parts[UNIT_OF_SALE_OUTERGENERICNAME];
            String ndc11 = parts[UNIT_OF_SALE_NDC11];
            String ndcAlt = parts[UNIT_OF_SALE_OUTERLABELER] + "-" + parts[UNIT_OF_SALE_OUTERPRODUCT] + "-"
                + parts[UNIT_OF_SALE_OUTERPACKAGE];
            addDeprecatedNdc(objectFactory, codeset, labeler, generic, ndc11, ndcAlt, label, lastUpdate,
                "Use full 11-digit format instead of 10-digit format");
            ndcAlt = ndc11.replaceAll("\\-", "");
            addDeprecatedNdc(objectFactory, codeset, labeler, generic, ndc11, ndcAlt, label, lastUpdate,
                "Use 11-digit format with dashes");
          }
          codeValuesAlreadyDefined.remove(ndc);
        }
      }
    }
    finishNdc(objectFactory, codeset, countTotal, codeValuesAlreadyDefined, in);
    return codeset;
  }

  private void finishNdc(ObjectFactory objectFactory, Codeset codeset, int countTotal,
      Set<String> codeValuesAlreadyDefined, BufferedReader in) throws IOException {
    System.out.println("  + added:   " + (codeset.getCode().size() - countTotal));
    System.out.println("  + expired: " + codeValuesAlreadyDefined.size());
    setUseDateAfter(objectFactory, codeset, codeValuesAlreadyDefined);
    System.out.println("  + total:   " + codeset.getCode().size());
    in.close();
  }

  private void setUseDateAfter(ObjectFactory objectFactory, Codeset codeset, Set<String> codeValuesAlreadyDefined) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.MONTH, 6);
    String sixMonthsFromNow = sdf.format(calendar.getTime());
    calendar.add(Calendar.MONTH, 6);
    String yearFromNow = sdf.format(calendar.getTime());
    for (Codeset.Code code : codeset.getCode()) {
      if (codeValuesAlreadyDefined.contains(code.getValue())) {
        System.err.println("  + NDC " + code.getValue() + " was dropped from CDC list! Setting to expire in 1 year. ");
        setUseDateNotExpectedAfter(sixMonthsFromNow, code, objectFactory);
        setUseDateNotAfter(yearFromNow, code, objectFactory);
      }
    }
  }

  private void linkToCvx(ObjectFactory objectFactory, Code code, String cvxCode) {
    if (cvxCode.length() > 0) {
      Reference reference = getReference(objectFactory, code);
      boolean found = false;
      for (LinkTo linkTo : reference.getLinkTo()) {
        if (linkTo.getCodeset().equals(CODE_SET_CVX) && linkTo.getValue().equals(cvxCode)) {
          found = true;
          break;
        }
      }
      if (!found) {
        LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
        reference.getLinkTo().add(linkTo);
        linkTo.setCodeset(CODE_SET_CVX);
        linkTo.setValue(cvxCode);
      }
    }
  }

  private void setCodeStatusAsValid(ObjectFactory objectFactory, Code code) {
    if (code.getCodeStatus() == null) {
      Code.CodeStatus codeStatus = objectFactory.createCodesetCodeCodeStatus();
      code.setCodeStatus(codeStatus);
      codeStatus.setStatus("Valid");
    }
  }

  private void setUseDateNotAfter(String date, Code code, ObjectFactory objectFactory) {
    if (date.length() == 8) {
      if (code.getUseDate() == null || isEmpty(code.getUseDate().getNotAfter())) {
        UseDate useDate = getUseDate(objectFactory, code);
        useDate.setNotAfter(date);
      }
    }
  }

  private void setUseDateNotBefore(String date, Code code, ObjectFactory objectFactory) {
    if (date.length() == 8) {
      if (code.getUseDate() == null || isEmpty(code.getUseDate().getNotAfter())) {
        UseDate useDate = getUseDate(objectFactory, code);
        useDate.setNotBefore(date);
      }
    }
  }

  private void setUseDateNotExpectedAfter(String date, Code code, ObjectFactory objectFactory) {
    if (date.length() == 8) {
      if (code.getUseDate() == null || isEmpty(code.getUseDate().getNotAfter())) {
        UseDate useDate = getUseDate(objectFactory, code);
        useDate.setNotExpectedAfter(date);
      }
    }
  }

  private void addDeprecatedNdc(ObjectFactory objectFactory, Codeset codeset, String labeler, String generic,
      String ndc11, String ndcAlt, String label, String lastUpdate, String reason) {
    if (ndcAlt.length() > 3 && !ndcAlt.equals(ndc11)) {
      Code code10 = objectFactory.createCodesetCode();
      codeset.getCode().add(code10);
      code10.setLabel(label);
      code10.setValue(ndcAlt);
      code10.setDescription(labeler + " - " + generic);
      Code.CodeStatus codeStatus10 = objectFactory.createCodesetCodeCodeStatus();
      code10.setCodeStatus(codeStatus10);
      codeStatus10.setStatus("Deprecated");
      Codeset.Code.CodeStatus.Deprecated deprecated = objectFactory.createCodesetCodeCodeStatusDeprecated();
      codeStatus10.setDeprecated(deprecated);
      SimpleDateFormat sdfIn = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
      SimpleDateFormat sdfOut = new SimpleDateFormat("yyyyMMdd");
      try {
        deprecated.setEffectiveDate(sdfOut.format(sdfIn.parse(lastUpdate)));
      } catch (ParseException e) {
        e.printStackTrace();
      }
      deprecated.setNewCodeValue(ndc11);
      deprecated.setReason(reason);
    }
  }

  private UseDate getUseDate(ObjectFactory objectFactory, Code code) {
    UseDate useDate = code.getUseDate();
    if (useDate == null) {
      useDate = objectFactory.createCodesetCodeUseDate();
      code.setUseDate(useDate);
    }
    return useDate;
  }

  private String[] readAndTrim(String line) {
    String[] parts = line.split("\\|");
    for (int i = 0; i < parts.length; i++) {
      parts[i] = parts[i].trim();
    }
    return parts;
  }

  private Codeset readCodsetUnitOfUse(Map<String, Code> codeMap) throws FileNotFoundException, IOException {
    System.out.println("Reading Unit of Use file");
    ObjectFactory objectFactory = new ObjectFactory();
    Codeset codeset = unmarshalCodeset(VACCINATION_NDC_CODE_UNIT_OF_USE_XML);
    int countTotal = codeset.getCode().size();
    Set<String> codeValuesAlreadyDefined = setupCodeValuesAlreadyDefined(codeset);
    BufferedReader in = new BufferedReader(new FileReader(unitUseFile));
    String line;
    while ((line = in.readLine()) != null) {
      if (line.length() > UNIT_OF_USE_NDC11) {
        String[] parts = readAndTrim(line);
        String innerId = parts[UNIT_OF_USE_NDCINNERID];
        if (parts.length > UNIT_OF_USE_NDC11 && !codeMap.containsKey(innerId)) {
          String ndc = parts[UNIT_OF_USE_NDC11];
          checkNDC(parts, ndc);
          boolean justCreated = false;
          Code code = getOrCreateCode(codeset, ndc);
          if (isEmpty(code.getLabel())) {
            code.setLabel(parts[UNIT_OF_USE_USEUNITPROPNAME]);
            justCreated = true;
          }
          if (isEmpty(code.getDescription())) {
            code.setDescription(parts[UNIT_OF_USE_USEUNITLABELERNAME] + " - " + parts[UNIT_OF_USE_USEUNITGENERICNAME]);
          }
          setCodeStatusAsValid(objectFactory, code);
          String cvxCode = parts[UNIT_OF_USE_CVX_CODE];
          linkToCvx(objectFactory, code, cvxCode);
          codeMap.put(innerId, code);
          setUseDateNotBefore(parts[UNIT_OF_USE_USEUNITSTARTDATE], code, objectFactory);
          setUseDateNotAfter(parts[UNIT_OF_USE_USEUNITENDDATE], code, objectFactory);
          if (justCreated) {
            String label = parts[UNIT_OF_USE_USEUNITPROPNAME];
            String lastUpdate = parts[UNIT_OF_USE_LAST_UPDATED_DATE];
            String labeler = parts[UNIT_OF_USE_USEUNITLABELERNAME];
            String generic = parts[UNIT_OF_USE_USEUNITGENERICNAME];
            String ndc11 = parts[UNIT_OF_USE_NDC11];
            String ndcAlt = parts[UNIT_OF_USE_USEUNITLABELER] + "-" + parts[UNIT_OF_USE_USEUNITPRODUCT] + "-"
                + parts[UNIT_OF_USE_USEUNITPACKAGE];
            addDeprecatedNdc(objectFactory, codeset, labeler, generic, ndc11, ndcAlt, label, lastUpdate,
                "Use full 11-digit format instead of 10-digit format");
            ndcAlt = ndc11.replaceAll("\\-", "");
            addDeprecatedNdc(objectFactory, codeset, labeler, generic, ndc11, ndcAlt, label, lastUpdate,
                "Use 11-digit format with dashes");
          }
          codeValuesAlreadyDefined.remove(ndc);
        }
      }
    }
    finishNdc(objectFactory, codeset, countTotal, codeValuesAlreadyDefined, in);
    return codeset;
  }

  private Set<String> setupCodeValuesAlreadyDefined(Codeset codeset) {
    Set<String> codeValuesAlreadyDefined = new HashSet<>();
    for (Codeset.Code code : codeset.getCode()) {
      if ((code.getUseDate() == null || isEmpty(code.getUseDate().getNotAfter()))
          && (code.getCodeStatus() == null || code.getCodeStatus().getDeprecated() == null)) {
        codeValuesAlreadyDefined.add(code.getValue());
      }
    }
    return codeValuesAlreadyDefined;
  }

  private void checkNDC(String[] parts, String ndc) {
    if (ndc.length() != 13) {
      throw new IllegalArgumentException("NDC is not the expected length, found \"" + ndc + "\" for inner id "
          + parts[0] + " but it does not look like an NDC");
    }
  }

  private static final int UNIT_OF_SALE_NDCOUTERID = 0;
  private static final int UNIT_OF_SALE_OUTERLABELER = 1;
  private static final int UNIT_OF_SALE_OUTERPRODUCT = 2;
  private static final int UNIT_OF_SALE_OUTERPACKAGE = 3;
  private static final int UNIT_OF_SALE_OUTERPROPNAME = 4;
  private static final int UNIT_OF_SALE_OUTERGENERICNAME = 5;
  private static final int UNIT_OF_SALE_OUTERLABELERNAME = 6;
  private static final int UNIT_OF_SALE_OUTERSTARTDATE = 7;
  private static final int UNIT_OF_SALE_OUTERENDDATE = 8;
  private static final int UNIT_OF_SALE_OUTERPACKFORM = 9;
  private static final int UNIT_OF_SALE_OUTERROUTE = 10;
  private static final int UNIT_OF_SALE_LAST_UPDATED_DATE = 11;
  private static final int UNIT_OF_SALE_CVX_CODE = 12;
  private static final int UNIT_OF_SALE_CVX_SHORT_DESCRIPTION = 13;
  private static final int UNIT_OF_SALE_NDC11 = 14;
  private static final int UNIT_OF_SALE_GTIN = 15;

  private static final int UNIT_OF_USE_NDCINNERID = 0;
  private static final int UNIT_OF_USE_USEUNITLABELER = 1;
  private static final int UNIT_OF_USE_USEUNITPRODUCT = 2;
  private static final int UNIT_OF_USE_USEUNITPACKAGE = 3;
  private static final int UNIT_OF_USE_USEUNITPROPNAME = 4;
  private static final int UNIT_OF_USE_USEUNITGENERICNAME = 5;
  private static final int UNIT_OF_USE_USEUNITLABELERNAME = 6;
  private static final int UNIT_OF_USE_USEUNITSTARTDATE = 7;
  private static final int UNIT_OF_USE_USEUNITENDDATE = 8;
  private static final int UNIT_OF_USE_USEUNITPACKFORM = 9; // Missing!
  private static final int UNIT_OF_USE_USEUNITGTIN = 10;
  private static final int UNIT_OF_USE_CVX_CODE = 11;
  private static final int UNIT_OF_USE_CVX_SHORT_DESCRIPTION = 12;
  private static final int UNIT_OF_USE_NOINNER = 13;
  private static final int UNIT_OF_USE_NDC11 = 14;
  private static final int UNIT_OF_USE_LAST_UPDATED_DATE = 15;
  private static final int UNIT_OF_USE_GTIN = 16;

  // private static final int LINKER_LINKER_ID = 0;
  private static final int LINKER_OUTER_ID = 1;
  private static final int LINKER_INNER_ID = 2;
  private static final int LINKER_MVX = 3;

  private void readLinkFile() throws FileNotFoundException, IOException {
    System.out.println("Reading link file");
    BufferedReader in = new BufferedReader(new FileReader(linkerFile));
    String line;
    while ((line = in.readLine()) != null) {
      if (line.length() > 3) {
        String[] parts = readAndTrim(line);
        if (parts.length > LINKER_MVX && parts[LINKER_OUTER_ID].length() > 0 && parts[LINKER_INNER_ID].length() > 0) {
          Link link = new Link();
          link.outerId = parts[LINKER_OUTER_ID];
          link.innerId = parts[LINKER_INNER_ID];
          link.mvx = parts[LINKER_MVX];
          getOuterLinkSet(link).add(link);
          getInnerLinkSet(link).add(link);
        }
      }
    }
    System.out.println("  + found " + linkMapSetByOuterId.size() + " outer id links");
    System.out.println("  + found " + linkMapSetByInnerId.size() + " inner id links");
    in.close();
  }

  private Set<Link> getOuterLinkSet(Link link) {
    Set<Link> linkSet = linkMapSetByOuterId.get(link.outerId);
    if (linkSet == null) {
      linkSet = new HashSet<>();
      linkMapSetByOuterId.put(link.outerId, linkSet);
    }
    return linkSet;
  }

  private Set<Link> getInnerLinkSet(Link link) {
    Set<Link> linkSet = linkMapSetByInnerId.get(link.innerId);
    if (linkSet == null) {
      linkSet = new HashSet<>();
      linkMapSetByInnerId.put(link.innerId, linkSet);
    }
    return linkSet;
  }

  private static boolean isEmpty(String s) {
    return s == null || s.trim().equals("");
  }

}

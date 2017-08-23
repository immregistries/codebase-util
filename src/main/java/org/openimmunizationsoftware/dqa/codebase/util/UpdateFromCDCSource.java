package org.openimmunizationsoftware.dqa.codebase.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.openimmunizationsoftware.dqa.codebase.util.gen.codebase.Codebase.Codeset.Code.CodeStatus;
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
  private static final String VACCINE_GROUP_XML = "Vaccine Group.xml";
  private static final String VACCINATION_CVX_CODE_XML = "Vaccination CVX Code.xml";
  private static final String VACCINATION_NDC_CODE_UNIT_OF_USE_XML = "Vaccination NDC Code Unit-of-Use.xml";
  private static final String VACCINATION_NDC_CODE_UNIT_OF_SALE_XML = "Vaccination NDC Code Unit-of-Sale.xml";

  private static final String CODE_SET_UNIT_OF_USE_LABEL = "Vaccination NDC for Unit-of-Use";
  private static final String CODE_SET_UNIT_OF_SALE_LABEL = "Vaccination NDC for Unit-of-Sale";
  private static final String CODE_SET_UNIT_OF_USE_TYPE = "VACCINATION_NDC_CODE_UNIT_OF_USE";
  private static final String CODE_SET_UNIT_OF_SALE_TYPE = "VACCINATION_NDC_CODE_UNIT_OF_SALE";
  private static final String CODE_SET_CVX = "VACCINATION_CVX_CODE";
  private static final String CODE_SET_MVX = "VACCINATION_MANUFACTURER_CODE";
  private static final String CODE_SET_VACCINE_GROUP = "VACCINE_GROUP";

  public static final String DEFAULT_CODEBASE_LOCATION = "../codebase";

  private File baseLocationFile;
  private File setLocationFile;
  private File cdcSourceLocationFile;
  private File linkerFile;
  private File unitSaleFile;
  private File unitUseFile;
  private File cvxFile;
  private File vac2vgFile;

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
                  SimpleDateFormat sdf = new SimpleDateFormat("M/d/yyyy");
                  try {
                    lastUpdate = sdf.parse(clean(n3Node.getTextContent().trim()));
                  } catch (DOMException | ParseException e) {
                    e.printStackTrace();
                  }
                }
              }
              if (!cvxCode.equals("")) {
                Codeset.Code c = getOrCreateCode(codeset, cvxCode);
                if (isEmpty(c.getLabel())) {
                  c.setLabel(shortDescription);
                }
                if (isEmpty(c.getDescription())) {
                  c.setDescription(fullVaccinename);
                }
                if (notes.length() > 0) {
                  c.setDescription(c.getDescription() + " Notes: " + notes);
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

                if (lastUpdate != null && (status.equals("Inactive") || status.equals("Active"))) {
                  SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                  UseDate useDate = getUseDate(objectFactory, c);
                  if (status.equals("Inactive") && useDate.getNotAfter() == null) {
                    useDate.setNotAfter(sdf.format(lastUpdate));
                  } else if (status.equals("Active") && useDate.getNotBefore() == null) {
                    useDate.setNotBefore(sdf.format(lastUpdate));
                  }
                }
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

  private void updateVaccineGroup() throws IOException {
    System.out.println("Vaccine Group");
    String filename = VACCINE_GROUP_XML;
    Codeset codeset = unmarshalCodeset(filename);
    int countTotal = codeset.getCode().size();
    int countAdded = 0;
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
              String shortDescription = nameValueMap.get("ShortDescription");
              String cvxCode = nameValueMap.get("CVXCode");
              // String status = nameValueMap.get("Status");
              String vaccineGroupName = nameValueMap.get("Vaccine Group Name");
              String cvxForVaccineGroup = nameValueMap.get("CVX for Vaccine Group");
              Codeset.Code c = getOrCreateCode(codeset, vaccineGroupName);

              if (isEmpty(c.getLabel())) {
                c.setLabel(shortDescription);
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
      marshalCodeset(codeset, filename);
      countAdded = codeset.getCode().size() - countTotal;
      countTotal = codeset.getCode().size();
      System.out.println("  + Added:   " + countAdded);
      System.out.println("  + Total:   " + countTotal);
    }
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
            LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
            reference.getLinkTo().add(linkTo);
            linkTo.setCodeset(CODE_SET_UNIT_OF_USE_TYPE);
            linkTo.setValue(innerCode.getValue());
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
            LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
            reference.getLinkTo().add(linkTo);
            linkTo.setCodeset(CODE_SET_UNIT_OF_SALE_TYPE);
            linkTo.setValue(outerCode.getValue());
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
    Codeset codeset = objectFactory.createCodeset();
    codeset.setType(CODE_SET_UNIT_OF_SALE_TYPE);
    codeset.setLabel(CODE_SET_UNIT_OF_SALE_LABEL);
    BufferedReader in = new BufferedReader(new FileReader(unitSaleFile));
    String line;
    while ((line = in.readLine()) != null) {
      if (line.length() > UNIT_OF_SALE_NDC11) {
        String[] parts = readAndTrim(line);
        if (parts.length > UNIT_OF_SALE_NDC11 && !codeMap.containsKey(parts[UNIT_OF_SALE_NDCOUTERID])) {
          Code code = objectFactory.createCodesetCode();
          codeset.getCode().add(code);
          code.setLabel(parts[UNIT_OF_SALE_OUTERPROPNAME]);
          code.setValue(parts[UNIT_OF_SALE_NDC11]);
          checkNDC(parts, code);
          code.setDescription(parts[UNIT_OF_SALE_OUTERLABELERNAME] + " - " + parts[UNIT_OF_SALE_OUTERGENERICNAME]);
          Code.CodeStatus codeStatus = objectFactory.createCodesetCodeCodeStatus();
          code.setCodeStatus(codeStatus);
          codeStatus.setStatus("Valid");
          if (parts[UNIT_OF_SALE_CVX_CODE].length() > 0) {
            LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
            Reference reference = getReference(objectFactory, code);
            reference.getLinkTo().add(linkTo);
            linkTo.setCodeset(CODE_SET_CVX);
            linkTo.setValue(parts[UNIT_OF_SALE_CVX_CODE]);
          }
          codeMap.put(parts[UNIT_OF_SALE_NDCOUTERID], code);
          if (parts[UNIT_OF_SALE_OUTERSTARTDATE].length() == 8) {
            UseDate useDate = getUseDate(objectFactory, code);
            useDate.setNotBefore(parts[UNIT_OF_SALE_OUTERSTARTDATE]);
          }
          if (parts[UNIT_OF_SALE_OUTERENDDATE].length() == 8) {
            UseDate useDate = getUseDate(objectFactory, code);
            useDate.setNotAfter(parts[UNIT_OF_SALE_OUTERENDDATE]);
          }
          {
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
        }
      }
    }
    in.close();
    System.out.println("  + found " + codeset.getCode().size() + " Unit of Sale NDCs");
    return codeset;
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
    Codeset codeset = objectFactory.createCodeset();
    codeset.setType(CODE_SET_UNIT_OF_USE_TYPE);
    codeset.setLabel(CODE_SET_UNIT_OF_USE_LABEL);
    BufferedReader in = new BufferedReader(new FileReader(unitUseFile));
    String line;
    while ((line = in.readLine()) != null) {
      if (line.length() > UNIT_OF_USE_NDC11) {
        String[] parts = readAndTrim(line);
        if (parts.length > UNIT_OF_USE_NDC11 && !codeMap.containsKey(parts[UNIT_OF_USE_NDCINNERID])) {
          Code code = objectFactory.createCodesetCode();
          codeset.getCode().add(code);
          code.setLabel(parts[UNIT_OF_USE_USEUNITPROPNAME]);
          code.setValue(parts[UNIT_OF_USE_NDC11]);
          checkNDC(parts, code);
          code.setDescription(parts[UNIT_OF_USE_USEUNITLABELERNAME] + " - " + parts[UNIT_OF_USE_USEUNITGENERICNAME]);
          Code.CodeStatus codeStatus = objectFactory.createCodesetCodeCodeStatus();
          code.setCodeStatus(codeStatus);
          codeStatus.setStatus("Valid");
          if (parts[UNIT_OF_USE_CVX_CODE].length() > 0) {
            LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
            Reference reference = getReference(objectFactory, code);
            reference.getLinkTo().add(linkTo);
            linkTo.setCodeset(CODE_SET_CVX);
            linkTo.setValue(parts[UNIT_OF_USE_CVX_CODE]);
          }
          codeMap.put(parts[UNIT_OF_USE_NDCINNERID], code);
          if (parts[UNIT_OF_USE_USEUNITSTARTDATE].length() == 8) {
            UseDate useDate = getUseDate(objectFactory, code);
            useDate.setNotBefore(parts[UNIT_OF_USE_USEUNITSTARTDATE]);
          }
          if (parts[UNIT_OF_USE_USEUNITENDDATE].length() == 8) {
            UseDate useDate = getUseDate(objectFactory, code);
            useDate.setNotAfter(parts[UNIT_OF_USE_USEUNITENDDATE]);
          }
          {
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

        }
      }
    }
    in.close();
    System.out.println("  + found " + codeset.getCode().size() + " Unit of Use NDCs");
    return codeset;
  }

  private void checkNDC(String[] parts, Code code) {
    if (code.getValue().length() != 13) {
      throw new IllegalArgumentException("NDC is not the expected length, found \"" + code.getValue()
          + "\" for inner id " + parts[0] + " but it does not look like an NDC");
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
  // private static final int UNIT_OF_USE_USEUNITPACKFORM = 9;  // Missing!
  private static final int UNIT_OF_USE_USEUNITGTIN = 9;
  private static final int UNIT_OF_USE_CVX_CODE = 10;
  private static final int UNIT_OF_USE_CVX_SHORT_DESCRIPTION = 11;
  private static final int UNIT_OF_USE_NOINNER = 12;
  private static final int UNIT_OF_USE_NDC11 = 13;
  private static final int UNIT_OF_USE_LAST_UPDATED_DATE = 14;
  private static final int UNIT_OF_USE_GTIN = 15;

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

package org.openimmunizationsoftware.dqa.codebase.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.Codeset;
import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.Codeset.Code;
import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.Codeset.Code.Reference;
import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.Codeset.Code.Reference.LinkTo;
import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.Codeset.Code.UseDate;
import org.openimmunizationsoftware.dqa.codebase.util.gen.codeset.ObjectFactory;

public class UpdateFromCDCSource
{
  private static final String CODE_SET_UNIT_OF_USE = "VACCINATION_NDC_CODE_UNIT_OF_USE";
  private static final String CODE_SET_UNIT_OF_SALE = "VACCINATION_NDC_CODE_UNIT_OF_SALE";
  private static final String CODE_SET_CVX = "VACCINATION_CVX_CODE";
  private static final String CODE_SET_MVX = "VACCINATION_MANUFACTURER_CODE";

  public static final String DEFAULT_CODEBASE_LOCATION = "../codebase";

  private File baseLocationFile;
  private File setLocationFile;
  private File cdcSourceLocationFile;
  private File linkerFile;
  private File unitSaleFile;
  private File unitUseFile;

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
  }

  public static void main(String[] args) throws IOException {
    UpdateFromCDCSource update = new UpdateFromCDCSource(args);
    update.go();
  }

  private class Link
  {
    public String outerId = "";
    public String innerId = "";
    public String mvx = "";
  }

  private Map<String, Code> codeMapOuter = new HashMap<>();
  private Map<String, Code> codeMapInner = new HashMap<>();

  private Map<String, Set<Link>> linkMapSetByOuterId = new HashMap<>();
  private Map<String, Set<Link>> linkMapSetByInnerId = new HashMap<>();

  public void go() throws IOException {
    readLinkFile();

    Codeset codesetUnitOfSale = readCodsetUnitOfSale(codeMapOuter);
    Codeset codesetUnitOfUse = readCodsetUnitOfUse(codeMapInner);

    linkUnitOfSale();
    linkUnitOfUse();

    saveCodeSet(codesetUnitOfSale, "Vaccination NDC Code Unit-of-Sale.xml");
    saveCodeSet(codesetUnitOfUse, "Vaccination NDC Code Unit-of-Use.xml");

  }

  private void linkUnitOfSale() {
    ObjectFactory objectFactory = new ObjectFactory();
    for (String outerId : linkMapSetByOuterId.keySet()) {
      Code outerCode = codeMapOuter.get(outerId);
      if (outerCode != null) {
        for (Link link : linkMapSetByOuterId.get(outerId)) {
          addMvx(objectFactory, link, outerCode);
          Code innerCode = codeMapInner.get(link.innerId);
          if (innerCode != null) {
            Reference reference = getReference(objectFactory, outerCode);
            LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
            reference.getLinkTo().add(linkTo);
            linkTo.setCodeset(CODE_SET_UNIT_OF_USE);
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
        for (Link link : linkMapSetByInnerId.get(innerId)) {
          addMvx(objectFactory, link, innerCode);
          Code outerCode = codeMapOuter.get(link.outerId);
          if (outerCode != null) {
            Reference reference = getReference(objectFactory, innerCode);
            LinkTo linkTo = objectFactory.createCodesetCodeReferenceLinkTo();
            reference.getLinkTo().add(linkTo);
            linkTo.setCodeset(CODE_SET_UNIT_OF_SALE);
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
    codeset.setLabel(CODE_SET_UNIT_OF_SALE);
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
          if (parts[UNIT_OF_SALE_OUTERSTARTDATE].length() == 8)
          {
            UseDate useDate = getUseDate(objectFactory, code);
            useDate.setNotBefore(parts[UNIT_OF_SALE_OUTERSTARTDATE]);
          }
          if (parts[UNIT_OF_SALE_OUTERENDDATE].length() == 8)
          {
            UseDate useDate = getUseDate(objectFactory, code);
            useDate.setNotAfter(parts[UNIT_OF_SALE_OUTERENDDATE]);
          }
        }
      }
    }
    in.close();
    System.out.println("  + found " + codeset.getCode().size() + " Unit of Sale NDCs");
    return codeset;
  }

  private UseDate getUseDate(ObjectFactory objectFactory, Code code) {
    UseDate useDate = code.getUseDate();
    if (useDate == null)
    {
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
    codeset.setLabel(CODE_SET_UNIT_OF_USE);
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
          if (parts[UNIT_OF_USE_USEUNITSTARTDATE].length() == 8)
          {
            UseDate useDate = getUseDate(objectFactory, code);
            useDate.setNotBefore(parts[UNIT_OF_USE_USEUNITSTARTDATE]);
          }
          if (parts[UNIT_OF_USE_USEUNITENDDATE].length() == 8)
          {
            UseDate useDate = getUseDate(objectFactory, code);
            useDate.setNotAfter(parts[UNIT_OF_USE_USEUNITENDDATE]);
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

}

package org.openimmunizationsoftware.dqa.codebase.util.gen;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.openimmunizationsoftware.dqa.codebase.util.gen.Codeset.Code;

import junit.framework.TestCase;

public class TestCodeSet extends TestCase
{
  public void testGenerate()
  {
    ObjectFactory objectFactory = new ObjectFactory();
    Codeset codeset = objectFactory.createCodeset();
    
    codeset.setLabel("VaccineCVX");
    Code code = objectFactory.createCodesetCode();
    codeset.getCode().add(code);
    code.setLabel("MMR");
    code.setValue("03");
    
    try
    {
      JAXBContext jaxbContext = JAXBContext.newInstance(Codeset.class);
      Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
      jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      jaxbMarshaller.marshal(codeset, System.out);
    }
    catch (JAXBException e)
    {
      e.printStackTrace();
    }
  }
}

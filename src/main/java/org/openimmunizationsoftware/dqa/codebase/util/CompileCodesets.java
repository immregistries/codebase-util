package org.openimmunizationsoftware.dqa.codebase.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;

public class CompileCodesets
{
  public static final String DEFAULT_BASE_LOCATION = "../codebase/base";

  public static void main(String[] args) throws IOException {
    String baseLocationString = DEFAULT_BASE_LOCATION;
    if (args.length > 0) {
      baseLocationString = args[0];
    }
    File baseLocationFile = new File(baseLocationString);
    if (!baseLocationFile.exists()) {
      System.err.println("Can't open code base location: " + baseLocationFile.getCanonicalPath());
      return;
    }
    File setLocationFile = new File(baseLocationFile, "sets");
    if (!setLocationFile.exists()) {
      System.err.println("Can't open code set location: " + setLocationFile.getCanonicalPath());
      return;
    }

    String setFilenames[] = setLocationFile.list(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(".xml");
      }
    });
    PrintWriter out = new PrintWriter(new FileWriter(new File(baseLocationFile, "Compiled.xml")));
    out.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    out.println("<codebase>");
    for (String setFilename : setFilenames) {
      System.out.println("Reading " + setFilename);
      BufferedReader in = new BufferedReader(new FileReader(new File(setLocationFile, setFilename)));
      String line = in.readLine();
      if (line != null) {
        if (!line.startsWith("<?xml")) {
          out.println("    " + line);
        }
        while ((line = in.readLine()) != null) {
          out.println("    " + line);
        }
      }
    }
    out.println("</codebase>");
    out.close();
  }
}

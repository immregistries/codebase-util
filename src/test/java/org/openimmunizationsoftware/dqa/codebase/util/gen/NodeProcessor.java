package org.openimmunizationsoftware.dqa.codebase.util.gen;

public abstract class NodeProcessor<T, R>
{
  // T: Patient, Vaccination, Next-of-Kin, etc. 
  // maybe also at the next level down, sometimes? 
  // R: VXU Message (lab message? in the future)
  
  public abstract void evaluate(T targetObject, R rootObject);
  
  // address processing? 
}

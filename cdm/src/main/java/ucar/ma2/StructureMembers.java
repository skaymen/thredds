/*
 * Copyright 1997-2008 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, strlenwrite to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package ucar.ma2;

import java.util.*;

/**
 * A Collection of members contained in a StructureData.
 *
 * @author caron
 */

public class StructureMembers {
  protected String name;
  protected Map<String, Member> memberHash = new HashMap<String, Member>(); // Hash of Members
  protected List<Member> members = new ArrayList<Member>(); // List of Members
  protected int structureSize = -1;

  public StructureMembers(String name) {
    this.name = name;
  }

  public StructureMembers(StructureMembers from) {
    this.name = from.name;
    for (Member m : from.members)
      addMember( new Member(m)); // make copy - without the data info
  }

  /**
   * Get the name.
   *
   * @return the name.
   */
  public String getName() {
    return name;
  }

  /**
   * Add a member.
   *
   * @param m member to add
   */
  public void addMember(Member m) {
    memberHash.put(m.getName(), m);
    members.add(m);
  }

  public Member addMember(String name, String desc, String units, DataType dtype, int[] shape) {
    Member m = new Member(name, desc, units, dtype, shape);
    addMember(m);
    return m;
  }

  /**
   * Get the total size of the Structure in bytes.
   *
   * @return the total size of the Structure in bytes.
   */
  public int getStructureSize() {
    if (structureSize < 0)
      calcStructureSize();
    return structureSize;
  }

  private void calcStructureSize() {
    structureSize = 0;
    for (Member member : members) {
      structureSize += member.getSizeBytes();
      // System.out.println(member.getName()+" size="+member.getTotalSize());
    }
  }

  /**
   * Set the total size of the Structure in bytes.
   *
   * @param structureSize set to this value
   */
  public void setStructureSize(int structureSize) {
    this.structureSize = structureSize;
  }

  /**
   * Get the list of Member objects.
   *
   * @return the list of Member objects.
   */
  public java.util.List<Member> getMembers() {
    return members;
  }


  /**
   * Get the names of the members.
   *
   * @return List of type String.
   */
  public java.util.List<String> getMemberNames() {
    List<String> memberNames = new ArrayList<String>();  // Strings
    for (Member m : members) {
      memberNames.add(m.getName());
    }
    return memberNames;
  }

  /**
   * Get the index-th member
   *
   * @param index of member
   * @return Member
   */
  public Member getMember(int index) {
    return members.get(index);
  }

  /**
   * Find the member by its name.
   *
   * @param memberName find by this name
   * @return Member matching the name, or null if not found
   */
  public Member findMember(String memberName) {
    if (memberName == null) return null;
    return memberHash.get(memberName);
  }

  /**
   * A member of a StructureData.
   */
  public class Member { 
    private String name, desc, units;
    private DataType dtype;
    private int size = 1;
    private int[] shape;
    private StructureMembers members; // only if member is type Structure

    // optional, use depends on ArrayStructure subclass
    private Array dataArray;
    private Object dataObject;
    private int dataParam;

    public Member(String name, String desc, String units, DataType dtype, int[] shape) {
      this.name = name;
      this.desc = desc;
      this.units = units;
      this.dtype = dtype;
      this.shape = shape;
      this.size = (int) Index.computeSize(shape);
    }

    public Member(Member from) {
      this.name = from.name;
      this.desc = from.desc;
      this.units = from.units;
      this.dtype = from.dtype;
      this.shape = from.shape;
      this.size = (int) Index.computeSize(shape);
      this.members = from.members;
    }

    /*
     * If member is type Structure, you must set its constituent members
     * @param members set to this value
     */
    public void setStructureMembers(StructureMembers members) {
      this.members = members;
    }

    public StructureMembers getStructureMembers() {
      return members;
    }

    public void setShape(int[] shape) {
      this.shape = shape;
      this.size = (int) Index.computeSize(shape);
    }

    /**
     * Get the name.
     *
     * @return the name.
     */
    public String getName() {
      return name;
    }

    /**
     * Get the units string, if any.
     *
     * @return the units string, or null if none.
     */
    public String getUnitsString() {
      return units;
    }

    /**
     * Get the description, if any.
     *
     * @return the description, or null if none.
     */
    public String getDescription() {
      return desc;
    }

    /**
     * Get the DataType.
     *
     * @return the DataType.
     */
    public DataType getDataType() {
      return dtype;
    }

    /**
     * Get the array shape. This does not have to match the VariableSimpleIF.
     *
     * @return the array shape.
     */
    public int[] getShape() {
      return shape;
    }

    /**
     * Get the total number of elements.
     * This does not have to match the VariableSimpleIF.
     *
     * @return the total number of elements.
     */
    public int getSize() {
      return size;
    }

    /**
     * Get the total size in bytes. This does not have to match the VariableSimpleIF.
     * Note that this will not be correct for type Sequence
     * @return total size in bytes
     */
    public int getSizeBytes() {
      if (getDataType() == DataType.SEQUENCE)
        return getDataType().getSize();
      else if (getDataType() == DataType.STRUCTURE)
        return size * members.getStructureSize();
      else
        return size * getDataType().getSize();
    }

    /**
     * Is this a scalar (size == 1).
     * This does not have to match the VariableSimpleIF.
     *
     * @return if this is a scalar
     */
    public boolean isScalar() {
      return size == 1;
    }

    ////////////////////////////////////////////////
    // these should not really be public

    /**
     * Get the data parameter value, for use behind the scenes.
     *
     * @return data parameter value
     */
    public int getDataParam() {
      return dataParam;
    }

    /**
     * Set the data parameter value, for use behind the scenes.
     *
     * @param dataParam set to this value
     */
    public void setDataParam(int dataParam) {
      this.dataParam = dataParam;
    }

    /**
     * Get the data array, if any. Used for implementation, DO NOT USE DIRECTLY!
     * @return  data object, may be null
     */
    public Array getDataArray() {
      return dataArray;
    }

    /**
     * Set the data array. Used for implementation, DO NOT USE DIRECTLY!
     * @param data set to this value
     */
    public void setDataArray(Array data) {
      this.dataArray = data;
      dtype = DataType.getType( data.getElementType());
      if (dtype == null)
        System.out.println("HEY");
    }

    /**
     * Get an opaque data object, for use behind the scenes. May be null
     * @return data object, may be null
     */
    public Object getDataObject() {
      return dataObject;
    }

    /**
     * Set an opaque data object, for use behind the scenes.
     * @param o set to this value
     */
    public void setDataObject(Object o) {
      this.dataObject = o;
    }

    public void setVariableInfo(String vname, DataType dtype, String unitString, String desc) {
      if (!this.name.equals(vname)) {
        memberHash.remove(name);
        name = vname;
        memberHash.put(name, this);
      }

      this.dtype = dtype;
      if (unitString != null)
        this.units = unitString;
      if (desc != null)
        this.desc = desc;
    }

    public String toString() { return name; }

  }

}

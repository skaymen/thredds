/* Copyright */
package ucar.nc2.ft2.coverage.remote;

import ucar.httpservices.HTTPFactory;
import ucar.httpservices.HTTPMethod;
import ucar.httpservices.HTTPSession;
import ucar.ma2.DataType;
import ucar.nc2.Attribute;
import ucar.nc2.AttributeContainerHelper;
import ucar.nc2.constants.AxisType;
import ucar.nc2.ft2.coverage.*;
import ucar.nc2.stream.NcStream;
import ucar.nc2.stream.NcStreamProto;
import ucar.nc2.time.Calendar;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarDateRange;
import ucar.unidata.geoloc.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Client side for opening a CdmrFeature CoverageDataset
 *
 * @author caron
 * @since 5/2/2015
 */
public class CdmrfReader {
  private static final boolean showRequest = true;
  String endpoint;

  public CdmrfReader(String endpoint) {
    this.endpoint = endpoint;
  }

  public boolean isCmrfEndpoint() throws IOException {

    HTTPSession httpClient = HTTPFactory.newSession(endpoint);
    String url = endpoint + "?req=header";

    // get the header
    try (HTTPMethod method = HTTPFactory.Head(httpClient, url)) {
      method.setFollowRedirects(true);
      if (showRequest) System.out.printf("CdmrFeature request %s %n", url);
      int statusCode = method.execute();

      return statusCode == 200;

    } catch (Throwable t) {
      return false;
    }
  }

  public CoverageDataset open() throws IOException {
    long start = System.currentTimeMillis();

    HTTPSession httpClient = HTTPFactory.newSession(endpoint);
    String url = endpoint + "?req=header";
    CdmrCoverageReader reader = new CdmrCoverageReader(endpoint, httpClient);

    // get the header
    try (HTTPMethod method = HTTPFactory.Get(httpClient, url)) {
      method.setFollowRedirects(true);
      if (showRequest) System.out.printf("CdmrFeature request %s %n", url);
      int statusCode = method.execute();

      if (statusCode == 404)
        throw new FileNotFoundException(getErrorMessage(method));

      if (statusCode >= 300)
        throw new IOException(getErrorMessage(method));

      InputStream is = method.getResponseAsStream();

      byte[] b = new byte[4];
      NcStream.readFully(is, b);

      if (!NcStream.test(b, NcStream.MAGIC_HEADER))
        throw new IOException("Data corrupted on " + endpoint);

      // header message
      int msize = NcStream.readVInt(is);
      byte[] m = new byte[msize];
      NcStream.readFully(is, m);

      CdmrFeatureProto.CoverageDataset proto = CdmrFeatureProto.CoverageDataset.parseFrom(m);
      CoverageDataset gridDataset =  decodeHeader(proto, reader);

      long took = System.currentTimeMillis() - start;
      if (showRequest) System.out.printf(" took %d msecs %n", took);
      return gridDataset;
    }

  }

  private static String getErrorMessage(HTTPMethod method) {
    String path = method.getURL();
    String status = method.getStatusLine();
    String content = method.getResponseAsString();
    return (content == null) ? path + " " + status : path + " " + status + "\n " + content;
  }


  /* message CalendarDateRange {
      required int64 start = 1;
      required int64 end = 2;
      required int32 calendar = 3; // ucar.nc2.time.Calendar ordinal
    }

    message CoverageDataset {
      required string name = 1;
      repeated Attribute atts = 2;
      required Rectangle latlonRect = 3;
      optional Rectangle projRect = 4;
      required CalendarDateRange dateRange = 5;

      repeated CoordSys coordSys = 6;
      repeated CoordTransform coordTransforms = 7;
      repeated CoordAxis coordAxes = 8;
      repeated Coverage grids = 9;
    } */
  CoverageDataset decodeHeader(CdmrFeatureProto.CoverageDataset proto, CdmrCoverageReader reader) {
    String name = endpoint;
    CoverageCoordSys.Type csysType = proto.hasCoverageType() ? convertCoverageType(proto.getCoverageType()) : null;
    LatLonRect latLonBoundingBox = decodeLatLonRectangle(proto.getLatlonRect());
    ProjectionRect projBoundingBox = decodeProjRectangle(proto.getProjRect());
    CalendarDateRange calendarDateRange = proto.hasDateRange() ? decodeDateRange(proto.getDateRange()) : null;

    AttributeContainerHelper gatts = new AttributeContainerHelper(name);
    for (ucar.nc2.stream.NcStreamProto.Attribute patt : proto.getAttsList())
      gatts.addAttribute(NcStream.decodeAtt(patt));

    List<CoverageCoordSys> coordSys = new ArrayList<>();
    for (CdmrFeatureProto.CoordSys pgrid : proto.getCoordSysList())
      coordSys.add(decodeCoordSys(pgrid));

    List<CoverageTransform> transforms = new ArrayList<>();
    for (CdmrFeatureProto.CoordTransform ptransform : proto.getCoordTransformsList())
      transforms.add(decodeCoordTransform(ptransform));

    List<CoverageCoordAxis> axes = new ArrayList<>();
    for (CdmrFeatureProto.CoordAxis paxes : proto.getCoordAxesList())
      axes.add(decodeCoordAxis(paxes, reader));

    List<Coverage> coverages = new ArrayList<>();
    for (CdmrFeatureProto.Coverage pgrid : proto.getGridsList())
      coverages.add(decodeGrid(pgrid, reader));

    //   public CoverageDataset(String name, AttributeContainerHelper atts, LatLonRect latLonBoundingBox, ProjectionRect projBoundingBox,
    //                         CalendarDateRange calendarDateRange, List<CoverageCoordSys > coordSys, List< CoverageCoordTransform > coordTransforms,
    //                         List< CoverageCoordAxis > coordAxes, List< Coverage > coverages) {

    return new CoverageDataset(name, csysType, gatts, latLonBoundingBox, projBoundingBox, calendarDateRange, coordSys, transforms, axes, coverages, reader);
  }

  /* message Rectangle {
      required double startx = 1;
      required double starty = 2;
      required double incx = 3;
      required double incy = 4;
    } */
  LatLonRect decodeLatLonRectangle(CdmrFeatureProto.Rectangle proto) {
    LatLonPointImpl start = new LatLonPointImpl(proto.getStarty(), proto.getStartx());
    return new LatLonRect(start, proto.getIncy(), proto.getIncx());
  }

  ProjectionRect decodeProjRectangle(CdmrFeatureProto.Rectangle proto) {
    ProjectionPoint pt = new ProjectionPointImpl(proto.getStartx(), proto.getStarty());
    return new ProjectionRect(pt, proto.getIncy(), proto.getIncx());
  }

  CalendarDateRange decodeDateRange(CdmrFeatureProto.CalendarDateRange proto) {
    ucar.nc2.time.Calendar cal = convertCalendar(proto.getCalendar());
    CalendarDate start = CalendarDate.of(cal, proto.getStart());
    CalendarDate end = CalendarDate.of(cal, proto.getEnd());
    return CalendarDateRange.of(start, end);
  }

  /*
message CoordSys {
  required string name = 1;               // must be unique in dataset's CoordSys
  repeated string axisNames = 2;
  repeated string transformNames = 3;
  optional CoverageType coverageType = 5;
}
   */
  CoverageCoordSys decodeCoordSys(CdmrFeatureProto.CoordSys proto) {
    //   public CoverageCoordSys(String name, List<String> axisNames, List<String> transformNames, Type type) {
    return new CoverageCoordSys(proto.getName(), proto.getAxisNamesList(), proto.getTransformNamesList(), convertCoverageType(proto.getCoverageType()));
  }

  /*
      message CoordTransform {
    required bool isHoriz = 1;
    required string name = 2;
    repeated Attribute params = 3;
  }
   */
  CoverageTransform decodeCoordTransform(CdmrFeatureProto.CoordTransform proto) {

    String name = proto.getName();
    AttributeContainerHelper atts = new AttributeContainerHelper(name);
    for (ucar.nc2.stream.NcStreamProto.Attribute patt : proto.getParamsList())
      atts.addAttribute(NcStream.decodeAtt(patt));

    //   public CoverageCoordTransform(String name, AttributeContainerHelper attributes, boolean isHoriz) {
    return new CoverageTransform(name, atts, proto.getIsHoriz());
  }

  /*
message CoordAxis {
  required string name = 1;          // short name, unique within dataset
  required DataType dataType = 2;
  repeated Attribute atts = 3;       // look for calendar attribute here?
  required AxisType axisType = 4;
  required string units = 5;
  optional string description = 6;

  required DependenceType depend = 7;
  optional string dependsOn = 8;    // depends on this axis

  required int64 nvalues = 10;
  required AxisSpacing spacing = 11;
  required double startValue = 12;
  required double endValue = 13;
  optional double resolution = 14;     // resolution = (end-start) / (nvalues-1)
  optional bytes values = 15;          // big endian doubles; not used for regular, may be deferred
}
   */
  CoverageCoordAxis decodeCoordAxis(CdmrFeatureProto.CoordAxis proto, CoordAxisReader reader) {
    AxisType axisType = convertAxisType(proto.getAxisType());
    String name = proto.getName();
    DataType dataType = NcStream.convertDataType(proto.getDataType());
    CoverageCoordAxis.DependenceType dependenceType = convertDependenceType(proto.getDepend());
    List<String> dependsOn = proto.getDependsOnList();
    CoverageCoordAxis.Spacing spacing = convertSpacing(proto.getSpacing());

    AttributeContainerHelper atts = new AttributeContainerHelper("axis atts");
    for (ucar.nc2.stream.NcStreamProto.Attribute patt : proto.getAttsList())
      atts.addAttribute(NcStream.decodeAtt(patt));

    int ncoords = (int) proto.getNvalues();
    double[] values = null;
    if (proto.hasValues()) {
      //
      // LOOK may mess with ability to change var size later.
      ByteBuffer bb = ByteBuffer.wrap(proto.getValues().toByteArray());
      DoubleBuffer db = bb.asDoubleBuffer();
      int n = db.remaining();
      values = new double[n];
      for (int i = 0; i < n; i++) values[i] = db.get(i);
    }

    if (dependenceType == CoverageCoordAxis.DependenceType.twoD && axisType == AxisType.Time) {

      return new FmrcTimeAxis2D(name, proto.getUnits(), proto.getDescription(), dataType, axisType, atts.getAttributes(), dependenceType, dependsOn,
                 spacing, ncoords, proto.getStartValue(), proto.getEndValue(), proto.getResolution(), values, reader);

    } else if (dependenceType == CoverageCoordAxis.DependenceType.twoD && (axisType == AxisType.Lat || axisType == AxisType.Lon)) {

      return new LatLonAxis2D(name, proto.getUnits(), proto.getDescription(), dataType, axisType, atts.getAttributes(), dependenceType, dependsOn,
                 spacing, ncoords, proto.getStartValue(), proto.getEndValue(), proto.getResolution(), values, reader);

    } else {

      return new CoverageCoordAxis1D(name, proto.getUnits(), proto.getDescription(), dataType, axisType, atts.getAttributes(), dependenceType, dependsOn,
              spacing, ncoords, proto.getStartValue(), proto.getEndValue(), proto.getResolution(), values, reader);
    }
  }

  /*
message Coverage {
  required string name = 1; // short name
  required DataType dataType = 2;
  optional bool unsigned = 3 [default = false];
  repeated Attribute atts = 4;
  required string coordSys = 5;
}
 */
  Coverage decodeGrid(CdmrFeatureProto.Coverage proto, CoverageReader reader) {
    DataType dataType = NcStream.convertDataType(proto.getDataType());

    List<Attribute> atts = new ArrayList<>();
    for (ucar.nc2.stream.NcStreamProto.Attribute patt : proto.getAttsList())
      atts.add(NcStream.decodeAtt(patt));

    return new Coverage(proto.getName(), dataType, atts, proto.getCoordSys(), proto.getUnits(), proto.getDescription(), reader);
  }

  static public AxisType convertAxisType(CdmrFeatureProto.AxisType dtype) {
    switch (dtype) {
      case RunTime:
        return AxisType.RunTime;
      case Ensemble:
        return AxisType.Ensemble;
      case Time:
        return AxisType.Time;
      case GeoX:
        return AxisType.GeoX;
      case GeoY:
        return AxisType.GeoY;
      case GeoZ:
        return AxisType.GeoZ;
      case Lat:
        return AxisType.Lat;
      case Lon:
        return AxisType.Lon;
      case Height:
        return AxisType.Height;
      case Pressure:
        return AxisType.Pressure;
      case RadialAzimuth:
        return AxisType.RadialAzimuth;
      case RadialDistance:
        return AxisType.RadialDistance;
      case RadialElevation:
        return AxisType.RadialElevation;
      case Spectral:
        return AxisType.Spectral;
      case TimeOffset:
        return AxisType.TimeOffset;
    }
    throw new IllegalStateException("illegal data type " + dtype);
  }

  static public Calendar convertCalendar(CdmrFeatureProto.Calendar type) {
    switch (type) {
      case gregorian:
        return Calendar.gregorian;
      case proleptic_gregorian:
        return Calendar.proleptic_gregorian;
      case noleap:
        return Calendar.noleap;
      case all_leap:
        return Calendar.all_leap;
      case uniform30day:
        return Calendar.uniform30day;
      case julian:
        return Calendar.julian;
      case none:
        return Calendar.none;
    }
    throw new IllegalStateException("illegal data type " + type);
  }

  //   public enum Type {Coverage, Curvilinear, Grid, Swath, Fmrc}

  static public CoverageCoordSys.Type convertCoverageType(CdmrFeatureProto.CoverageType type) {
    switch (type) {
      case General:
        return CoverageCoordSys.Type.General;
      case Curvilinear:
        return CoverageCoordSys.Type.Curvilinear;
      case Grid:
        return CoverageCoordSys.Type.Grid;
      case Swath:
        return CoverageCoordSys.Type.Swath;
      case Fmrc:
        return CoverageCoordSys.Type.Fmrc;
    }
    throw new IllegalStateException("illegal CoverageType " + type);
  }

  static public CoverageCoordAxis.DependenceType convertDependenceType(CdmrFeatureProto.DependenceType type) {
    switch (type) {
      case independent:
        return CoverageCoordAxis.DependenceType.independent;
      case dependent:
        return CoverageCoordAxis.DependenceType.dependent;
      case scalar:
        return CoverageCoordAxis.DependenceType.scalar;
      case twoD:
        return CoverageCoordAxis.DependenceType.twoD;
    }
    throw new IllegalStateException("illegal data type " + type);
  }


  static public CoverageCoordAxis.Spacing convertSpacing(CdmrFeatureProto.AxisSpacing type) {
    switch (type) {
      case regular:
        return CoverageCoordAxis.Spacing.regular;
      case irregularPoint:
        return CoverageCoordAxis.Spacing.irregularPoint;
      case contiguousInterval:
        return CoverageCoordAxis.Spacing.contiguousInterval;
      case discontiguousInterval:
        return CoverageCoordAxis.Spacing.discontiguousInterval;
    }
    throw new IllegalStateException("illegal data type " + type);
  }

    ///////////////////////////////////////////////////////////////////

  /*
    message DataResponse {
    repeated CoordAxis coordAxes = 1;              // may be shared if asking for multiple grids
    repeated CoordSys coordSys = 2;                // may be shared if asking for multiple grids

    repeated GeoReferencedArray geoArray = 4;
  }
   */
  public DataResponse decodeDataResponse(CdmrFeatureProto.DataResponse dproto) {
    List<CoverageTransform> transforms = new ArrayList<>();
    for (CdmrFeatureProto.CoordTransform pt : dproto.getCoordTransformsList())
      transforms.add( decodeCoordTransform(pt));

    List<CoverageCoordSys> coordSys = new ArrayList<>();
    for (CdmrFeatureProto.CoordSys psys : dproto.getCoordSysList())
      coordSys.add( decodeCoordSys(psys));

    List<CoverageCoordAxis> axes = new ArrayList<>();
    for (CdmrFeatureProto.CoordAxis paxis : dproto.getCoordAxesList())
      axes.add( decodeCoordAxis(paxis, null));  // LOOK null reader - so all values must be present

    List<GeoArrayResponse> arrays = new ArrayList<>();
    for (CdmrFeatureProto.GeoReferencedArray psys : dproto.getGeoArrayList())
      arrays.add(decodeGeoReferencedArray(psys));

    return new DataResponse(axes, coordSys, transforms, arrays);
  }

  /*
  message GeoReferencedArray {
    required string gridName = 1;          // full escaped name.
    required DataType dataType = 2;
    optional bool bigend = 3 [default = true];
    optional uint32 version = 4 [default = 0];
    optional Compress compress = 5 [default = NONE];
    optional uint64 uncompressedSize = 6;

    repeated uint32 shape = 7;            // the shape of the returned array
    repeated string axisName = 8;         // each dimension corresponds to this axis
    required string coordSysName = 9;     // must have coordAxis corresponding to shape
  }


   */

  public GeoArrayResponse decodeGeoReferencedArray(CdmrFeatureProto.GeoReferencedArray parray) {
    GeoArrayResponse result = new GeoArrayResponse();

    result.coverageName = parray.getCoverageName();
    result.dataType = NcStream.convertDataType(parray.getDataType());

    result.bigend = parray.getBigend();
    result.deflate = parray.getCompress() == NcStreamProto.Compress.DEFLATE;
    result.uncompressedSize = (parray.hasUncompressedSize()) ? parray.getUncompressedSize() : 0;

    int[] shape = new int[parray.getShapeCount()];
    for (int i=0; i< parray.getShapeCount(); i++)
      shape[i] = parray.getShape(i);
    result.shape = shape;

    result.axisName = parray.getAxisNameList();
    result.coordSysName = parray.getCoordSysName();

    return result;
  }

}

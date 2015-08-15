/* Copyright */
package ucar.nc2.ft2.coverage;

import ucar.nc2.AttributeContainer;
import ucar.nc2.constants.CDM;
import ucar.nc2.constants.CF;
import ucar.nc2.time.Calendar;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.time.CalendarDateUnit;
import ucar.nc2.util.NamedAnything;
import ucar.nc2.util.NamedObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for Time coordinates
 *
 * @author caron
 * @since 7/11/2015
 */
public class TimeHelper {
  final Calendar cal;
  final CalendarDateUnit dateUnit;
  final CalendarDate refDate;
  final double duration;

  public TimeHelper(String units, AttributeContainer atts) {
    this.cal = getCalendarFromAttribute(atts);
    this.dateUnit = CalendarDateUnit.withCalendar(cal, units); // this will throw exception on failure
    this.refDate = dateUnit.getBaseCalendarDate();
    this.duration = dateUnit.getTimeUnit().getValueInMillisecs();
  }

  public TimeHelper(AttributeContainer atts) {
    String units = atts.findAttValueIgnoreCase(CDM.UDUNITS, null);
    if (units == null)
      units = atts.findAttValueIgnoreCase(CDM.UNITS, null);
    this.cal = getCalendarFromAttribute(atts);
    this.dateUnit = CalendarDateUnit.withCalendar(cal, units); // this will throw exception on failure
    this.refDate = dateUnit.getBaseCalendarDate();
    this.duration = dateUnit.getTimeUnit().getValueInMillisecs();
  }

  // get offset from runDate, in units of dateUnit
  public double convert(CalendarDate date) {
    long msecs = date.getDifferenceInMsecs(refDate);
    return msecs / duration;
  }

  public List<NamedObject> getCoordValueNames(CoverageCoordAxis1D axis) {
    axis.getValues(); // read in if needed
    List<NamedObject> result = new ArrayList<>();
    for (int i = 0; i < axis.getNcoords(); i++) {
      double value;
      switch (axis.getSpacing()) {
        case regular:
        case irregularPoint:
          value = axis.getCoord(i);
          result.add(new NamedAnything(makeDate(value), axis.getAxisType().toString()));
          break;

        case contiguousInterval:
        case discontiguousInterval:
          CoordInterval coord = new CoordInterval(axis.getCoordEdge1(i), axis.getCoordEdge2(i), 3);  // LOOK
          result.add(new NamedAnything(coord, coord + " " + axis.getUnits()));
          break;
      }
    }

    return result;
  }

  public CalendarDate getRefDate() {
    return refDate;
  }

  public CalendarDate makeDate(double value) {
    return dateUnit.makeCalendarDate(value);
  }

  public CalendarDateRange getDateRange(double startValue, double endValue) {
    CalendarDate start = makeDate( startValue);
    CalendarDate end = makeDate( endValue);
    return CalendarDateRange.of(start, end);
  }

  public double getOffsetInTimeUnits(CalendarDate convertFrom, CalendarDate convertTo) {
    return dateUnit.getTimeUnit().getOffset(convertFrom, convertTo);
  }

  public static ucar.nc2.time.Calendar getCalendarFromAttribute(AttributeContainer atts) {
    String cal = atts.findAttValueIgnoreCase(CF.CALENDAR, null);
    if (cal == null) return null;
    return ucar.nc2.time.Calendar.get(cal);
  }
}
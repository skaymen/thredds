package thredds.server.wfs;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import ucar.nc2.ft2.coverage.simpgeometry.SimpleGeometry;

/**
 * A writer for a WFS compliant Feature Collection GML file.
 * Answers to GetFeature requests.
 * 
 * @author wchen@usgs.gov
 *
 */
public class WFSGetFeatureWriter {
	
	private PrintWriter response;
	private String fileOutput;
	private final String namespace;
	private final String server;
	private final String ftName;
	private List<SimpleGeometry> geometries;
	
	/**
	 * Writes headers and bounding box
	 */
	private void writeHeadersAndBB() {
		fileOutput += "<wfs:FeatureCollection xsi:schemaLocation=" + WFSXMLHelper.encQuotes("http://www.opengis.net/wfs/2.0 http://schemas.opengis.net/wfs/2.0/wfs.xsd " + namespace + " " + server
					+ "?request=DescribeFeatureType" + WFSXMLHelper.AMPERSAND + "service=wfs" + WFSXMLHelper.AMPERSAND + "version=2.0.0" + WFSXMLHelper.AMPERSAND + "typename=" 
					+ WFSController.TDSNAMESPACE + "%3A" + ftName)
				+ " xmlns:xsi=" + WFSXMLHelper.encQuotes("http://www.w3.org/2001/XMLSchema-instance")
				+ " xmlns:xlink=" + WFSXMLHelper.encQuotes("http://www.w3.org/1999/xlink")
				+ " xmlns:gml=" + WFSXMLHelper.encQuotes("http://opengis.net/gml/3.2")
				+ " xmlns:fes=" + WFSXMLHelper.encQuotes("http://www.opengis.net/fes/2.0")
				+ " xmlns:ogc=" + WFSXMLHelper.encQuotes("http://www.opengis.net/ogc")
				+ " xmlns:wfs=" + WFSXMLHelper.encQuotes("http://opengis.net/wfs/2.0") 
				+ " xmlns:" + WFSController.TDSNAMESPACE +"=" + WFSXMLHelper.encQuotes(namespace)
				+ " xmlns=" + WFSXMLHelper.encQuotes("http://www.opengis.net/wfs/2.0")
				+ " version=\"2.0.0\" numberMatched=" + WFSXMLHelper.encQuotes(String.valueOf(geometries.size())) + " numberReturned=" 
				+ WFSXMLHelper.encQuotes(String.valueOf(geometries.size())) + ">"
		
		   // WFS Bounding Box
			+ "<wfs:boundedBy>"
			+ "<wfs:Envelope srsName=" + "\"urn:ogc:def:crs:EPSG::4326\"" + ">"
					+ "<wfs:lowerCorner>" + "-180 -90" + "</wfs:lowerCorner>"
					+ "<wfs:upperCorner>" + "180 90" + "</wfs:upperCorner>"
			+ "</wfs:Envelope>"
			+ "</wfs:boundedBy>";
	}
	
	/**
	 * Initiate the response with an XML file with an XML header and the FeatureCollection tag. Write bounding box and namespace information.
	 */
	public void startXML() {
		fileOutput += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
		writeHeadersAndBB();
	}
	
	/**
	 * In the WFS specification for GetFeature each feature type is its own
	 * member and so writeMembers add each member to the fileOutput
	 */
	public void writeMembers() {
		int index = 1;
		GMLFeatureWriter writer = new GMLFeatureWriter();
		for(SimpleGeometry geometryItem: geometries) {
			fileOutput
				   += "<wfs:member>"
					
					// Write Geometry Information
					+ "<" + WFSController.TDSNAMESPACE + ":" + ftName + " gml:id=\"" + ftName + "." + index + "\">"
					
					// GML Bounding Box
					+ "<gml:boundedBy>"
					+ "<gml:Envelope srsName=" + "\"urn:ogc:def:crs:EPSG::4326\"" + ">"
							+ "<gml:lowerCorner>" + "-180 -90" + "</gml:lowerCorner>"
							+ "<gml:upperCorner>" + "180 90" + "</gml:upperCorner>"
					+ "</gml:Envelope>"
					+ "</gml:boundedBy>"
					
					+ "<" + WFSController.TDSNAMESPACE + ":catchments_geometry_container>";

			//write GML features
			fileOutput += writer.writeFeature(geometryItem);
					
			// Cap off headers
			fileOutput
					+="</" + WFSController.TDSNAMESPACE + ":catchments_geometry_container>"
					+ "</" + WFSController.TDSNAMESPACE + ":" + ftName +">"
					+ "</wfs:member>";
			
			index++;
		}
	}
	
	/**
	 * Finish writing the XML file, write the end tag for FeatureCollection and append it all to the PrintWriter.
	 * 
	 * Once a XML is finished, the WFSDataWriter is no longer usable.
	 */
	public void finishXML() {
		fileOutput += "</wfs:FeatureCollection>";
		response.append(fileOutput);
		fileOutput = null; response = null;
	}
	
	/**
	 * Opens a WFSGetFeatureWriter, writes to the response given.
	 * 
	 * @param response to write to
	 * @param server WFS Server URI
	 * @param namespace WFS TDS Namespace URI
	 * @throws IOException 
	 */
	public WFSGetFeatureWriter(PrintWriter response, String server, String namespace, List<SimpleGeometry> geometries, String ftName) {
		this.fileOutput = "";
		this.response = response;
		this.server = server;
		this.namespace = namespace;
		this.geometries = geometries;
		this.ftName = ftName;
	}
}

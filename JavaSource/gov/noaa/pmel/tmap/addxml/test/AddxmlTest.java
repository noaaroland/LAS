package gov.noaa.pmel.tmap.addxml.test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import gov.noaa.pmel.tmap.addxml.addXML;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.filter.ElementFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dods.DODSNetcdfFile;

public class AddxmlTest {

	
	@Before
	public void setUp() throws Exception {		
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Test
	public final void testOISST() {
		// This is a test of the hack in the code to identify an climatology from ESRL/PSD.  There should be a modulo=true attribute
		// on the time axis.
		String url = DODSNetcdfFile.canonicalURL("http://ferret.pmel.noaa.gov/thredds/dodsC/data/PMEL/sst.ltm.1971-2000.nc");
		NetcdfDataset ncds;
		try {
			addXML addxml = new addXML();
			HashMap<String, String> options= new HashMap<String, String>();
			addxml.setOptions(options);
			ncds = ucar.nc2.dataset.NetcdfDataset.openDataset(url);
			Document coads = addxml.createXMLfromNetcdfDataset(ncds , url);
			Element axes = coads.getRootElement().getChild("axes");
			List<Element> axisList = axes.getChildren();
			assertTrue(axisList.size() > 0);
			for (Iterator axisIt = axisList.iterator(); axisIt.hasNext();) {
				Element axis = (Element) axisIt.next();
				String type = axis.getAttributeValue("type");
				if ( type.equals("t") ) {
					String mod = axis.getAttributeValue("modulo");
					assertTrue(mod.equals("true"));
				}
			}
		} catch (IOException e) {
			fail("Unable to connect to OPeNDAP server.");
		}
	}
	
    @Test
    public final void testNGDC() {
    	// The sole purpose of this test it to check the starting hour of the time axis.
    	// The time axis is regular with an interval of 1 day, but for some strange reason the times are recorded at 17:00
    	// so the hour needs to be included in the start string.
    	String url = DODSNetcdfFile.canonicalURL("http://www.ngdc.noaa.gov/thredds/dodsC/sst-100km-aggregation");
		NetcdfDataset ncds;
		try {
			addXML addxml = new addXML();
			HashMap<String, String> options= new HashMap<String, String>();
			options.put("force","t");
			addxml.setOptions(options);
			ncds = ucar.nc2.dataset.NetcdfDataset.openDataset(url);
			Document ngdc = addxml.createXMLfromNetcdfDataset(ncds , url);
			Iterator<Element> arangeIt = ngdc.getDescendants(new ElementFilter("arange"));
			assertTrue(arangeIt.hasNext());
			while ( arangeIt.hasNext() ) {
				Element arange = (Element) arangeIt.next();
				String start = arange.getAttributeValue("start");
				if ( start.length() > 6) {
					assertTrue(start.endsWith("17:00:00"));
				}
			}
		} catch (IOException e) {
			fail("Unable to connect to OPeNDAP server.");
		}
    }
	@Test
	public final void testWOA() {
		String url = DODSNetcdfFile.canonicalURL("http://ferret.pmel.noaa.gov/thredds/dodsC/data/PMEL/WOA01/english/seasonal/sili_sea_mean_1deg.nc");
		NetcdfDataset ncds;
		try {
			addXML addxml = new addXML();
			HashMap<String, String> options= new HashMap<String, String>();
			options.put("force", "t");
			addxml.setOptions(options);
			ncds = ucar.nc2.dataset.NetcdfDataset.openDataset(url);
			Document woa = addxml.createXMLfromNetcdfDataset(ncds , url);
			Iterator<Element> arangeIt = woa.getDescendants(new ElementFilter("arange"));
			assertTrue(arangeIt.hasNext());
			// X-Axis
			// <arange start="0.5" size="360" step="1" />
			Element arange = arangeIt.next();
			assertTrue(arange.getAttributeValue("size").equals("360"));
			assertTrue(arange.getAttributeValue("start").equals("0.5"));
			assertTrue(arange.getAttributeValue("step").equals("1"));
			// Y-Axis
			// <arange start="-89.5" size="180" step="1" />
			assertTrue(arangeIt.hasNext());
			arange = arangeIt.next();
			assertTrue(arange.getAttributeValue("size").equals("180"));
			assertTrue(arange.getAttributeValue("start").equals("-89.5"));
			assertTrue(arange.getAttributeValue("step").equals("1"));
			// T-Axis
			// <arange start="0000-02-15" size="4" step="3" />
			assertTrue(arangeIt.hasNext());
			arange = arangeIt.next();
			assertTrue(arange.getAttributeValue("size").equals("4"));
			assertTrue(arange.getAttributeValue("start").equals("0000-02-15 15:00:00"));
			assertTrue(arange.getAttributeValue("step").equals("3"));
		} catch (IOException e) {
			fail("Unable to connect to OPeNDAP server.");
		}
	}
	@Test
	public final void testCOADS() {
		String url = DODSNetcdfFile.canonicalURL("http://ferret.pmel.noaa.gov/thredds/dodsC/data/PMEL/coads_climatology.nc");
		NetcdfDataset ncds;
		try {
			addXML addxml = new addXML();
			HashMap<String, String> options= new HashMap<String, String>();
			addxml.setOptions(options);
			ncds = ucar.nc2.dataset.NetcdfDataset.openDataset(url);
			// Test from COADS, which tests the new vectors capability
			Document coads = addxml.createXMLfromNetcdfDataset(ncds , url);
			
			
			Iterator<Element> compIt = coads.getDescendants(new ElementFilter("composite"));
			
			assertTrue(compIt.hasNext());
			
			Element composite = compIt.next();
			
			assertTrue(composite != null);
			
			List children = composite.getChildren();
			
			assertTrue(children.size() == 1);
			
			Element variable = (Element) children.get(0);
			
			assertTrue(variable != null);
			
			String name = variable.getAttributeValue("name");
			String units = variable.getAttributeValue("units");
			
			assertTrue(name.equals("Vector of ZONAL WIND and MERIDIONAL WIND"));
			assertTrue(units.equals("M/S"));
			
		} catch (IOException e) {
			fail("Unable to connect to OPeNDAP server.");
		}
		
	}
    @Test
    public final void testGFDL() {
    	// Test to make sure variables with names that start with u or v but aren't vectors don't get included.  There should only be one composite.
    	String url = DODSNetcdfFile.canonicalURL("http://data1.gfdl.noaa.gov:8380/thredds3/dodsC/dc_CM2.1_R1_Cntr-ITFblock_monthly_ocean_tripolar_01010101-02001231");
		String[] name = new String[]{"Vector of Grid_eastward Sea Water Velocity and Grid_northward Sea Water Velocity and Upward Sea Water Velocity"};
		String[] units = new String[]{"m s-1"};
		NetcdfDataset ncds;
		try {
			addXML addxml = new addXML();
			HashMap<String, String> options= new HashMap<String, String>();
			addxml.setOptions(options);
			ncds = ucar.nc2.dataset.NetcdfDataset.openDataset(url);
			// Test from COADS, which tests the new vectors capability
			Document leetmaa = addxml.createXMLfromNetcdfDataset(ncds , url);
			Iterator compIt = leetmaa.getRootElement().getDescendants(new ElementFilter("composite"));
			assertTrue(compIt.hasNext());
			int index = 0;
			while ( compIt.hasNext() ) {
				Element composite = (Element) compIt.next();
				List children = composite.getChildren();				
				assertTrue(children.size() == 1);
				Element variable = (Element) children.get(0);
				assertTrue(variable != null);
				String vname = variable.getAttributeValue("name");
				String vunits = variable.getAttributeValue("units");
				assertTrue(vname.equals(name[index]));
				assertTrue(vunits.equals(units[index]));
				index++;
			}
			assertTrue(index == 1);
		} catch (IOException e) {
			fail("Unable to connect to OPeNDAP server.");
		}
    }
	@Test
	public final void testLeetmaa() {
		String url = DODSNetcdfFile.canonicalURL("http://iridl.ldeo.columbia.edu/SOURCES/.NOAA/.NCEP/.EMC/.CMB/.Pacific/.monthly/dods");
		String[] name = new String[]{"Vector of zonal wind stress and meridional wind stress", "Vector of zonal velocity and meridional velocity"};
		String[] units = new String[]{"unitless", "cm/s"};
		NetcdfDataset ncds;
		try {
			addXML addxml = new addXML();
			HashMap<String, String> options= new HashMap<String, String>();
			addxml.setOptions(options);
			ncds = ucar.nc2.dataset.NetcdfDataset.openDataset(url);
			// Test from COADS, which tests the new vectors capability
			Document leetmaa = addxml.createXMLfromNetcdfDataset(ncds , url);
			Iterator compIt = leetmaa.getRootElement().getDescendants(new ElementFilter("composite"));
			assertTrue(compIt.hasNext());
			int index = 0;
			while ( compIt.hasNext() ) {
				Element composite = (Element) compIt.next();
				List children = composite.getChildren();				
				assertTrue(children.size() == 1);
				Element variable = (Element) children.get(0);
				assertTrue(variable != null);
				String vname = variable.getAttributeValue("name");
				String vunits = variable.getAttributeValue("units");
				assertTrue(vname.equals(name[index]));
				assertTrue(vunits.equals(units[index]));
				index++;
			}
			Iterator arIt = leetmaa.getDescendants(new ElementFilter("arange"));
			assertTrue(arIt.hasNext());
			while ( arIt.hasNext() ) {
				Element arange = (Element) arIt.next();
				String start = arange.getAttributeValue("start");
				if ( start.length() > 6 ) {
					// It's the time...
					assertTrue(start.equals("1980-01-01"));
				}
			}
		} catch (IOException e) {
			fail("Unable to connect to OPeNDAP server.");
		}
	}
}

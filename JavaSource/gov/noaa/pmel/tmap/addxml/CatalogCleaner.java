package gov.noaa.pmel.tmap.addxml;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.jdom.Element;

import thredds.catalog.InvAccess;
import thredds.catalog.InvCatalog;
import thredds.catalog.InvCatalogImpl;
import thredds.catalog.InvDataset;
import thredds.catalog.InvDatasetImpl;
import thredds.catalog.InvProperty;
import thredds.catalog.InvService;
import thredds.catalog.ServiceType;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dataset.CoordinateAxis1D;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.TypedDatasetFactory;

public class CatalogCleaner {

	private InvCatalogImpl sourceCatalog;
	private InvCatalogImpl cleanCatalog;
	private String key;
	private InvService remoteService;
	private InvService localService;
	private boolean aggregate = false;
	private int total;
	private int total_aggregations;
	private int total_files;
	private boolean done;
	private static final int MAX_ACCESS_POINTS = 100;
	private static final int MIN_AGGS = 10;
	private static final int MIN_FILES = 100;
	private static final int MAX_TOTAL_FILES = 1000;

	public CatalogCleaner (InvCatalog catalog, boolean aggregate) throws URISyntaxException, UnsupportedEncodingException {
		this.aggregate = aggregate;
		sourceCatalog = (InvCatalogImpl) catalog;
		key = JDOMUtils.MD5Encode(catalog.getUriString());
		cleanCatalog = new InvCatalogImpl("Clean Catalog for "+sourceCatalog.getUriString(), "1.0.1", new URI(catalog.getUriString()));
		localService = new InvService("localOPeNDAP_"+key, "OPeNDAP", "/thredds/dodsC/", null, null);
		cleanCatalog.addService(localService);
	}
	public InvCatalogImpl cleanCatalog() throws Exception {
		done = false;
		total = 0;
		List<InvDataset> threddsDatasets = sourceCatalog.getDatasets();
		for (Iterator dsIt = threddsDatasets.iterator(); dsIt.hasNext();) {
			InvDataset invDataset = (InvDataset) dsIt.next();
			if ( invDataset.hasAccess() ) {
				if ( hasGrid(invDataset) ) {	
					total++;
					addGridDataset(invDataset);
				}
			}
			if ( invDataset.hasNestedDatasets() ) {
				if ( done ) {
					cleanCatalog.finish();
					return cleanCatalog;
				}
				clean(invDataset);

			}
		}
		cleanCatalog.finish();
		return cleanCatalog;
	}
	private void addGridDataset(InvDataset invDataset) {
		total_files++;
		InvAccess access = invDataset.getAccess(ServiceType.OPENDAP);
		String url = access.getUrlPath();
		if ( remoteService == null ) {
			String full_url = access.getStandardUri().toString();
			String base = full_url.substring(0, full_url.indexOf(url));
			setService(base);	
		}
		InvDatasetImpl dataset = new InvDatasetImpl((InvDatasetImpl)invDataset);
		dataset.setServiceName(remoteService.getName());
		dataset.setUrlPath(url);
		dataset.setID(dataset.getUrlPath()+"_"+key);
		dataset.setDataType(FeatureType.GRID);
		dataset.finish();
		cleanCatalog.addDataset(dataset);
	}
	private void setService(String base) {		
		remoteService = new InvService("remoteOPeNDAP_"+key, "OPeNDAP", base, null, null);
		cleanCatalog.addService(remoteService);
	}
	private void addAggregation(InvDatasetImpl parent, InvDataset invDataset, List<DatasetGridPair> agg, int index) throws Exception {
		total_aggregations++;
		InvDatasetImpl aggDatasetNode = new InvDatasetImpl((InvDatasetImpl)invDataset);
		aggDatasetNode.setName(aggDatasetNode.getName()+" "+index);
		aggDatasetNode.setUrlPath("aggregation_"+index);
		aggDatasetNode.setID(aggDatasetNode.getUrlPath()+"_"+key+"_"+index);
		Element ncml = NCML.getRootElement();
		NCML.addAggregationElement(ncml, agg.get(0).getGrid());
		for (int i = 0; i < agg.size(); i++ ) {
			GridDataset aggDataset = (GridDataset) agg.get(i).getGrid();
			NCML.addDataset(ncml, aggDataset);
		}
		aggDatasetNode.setServiceName(localService.getName());
		aggDatasetNode.setNcmlElement(ncml);
		aggDatasetNode.setDataType(FeatureType.GRID);
		aggDatasetNode.finish();
		parent.addDataset(aggDatasetNode);
	}
	public void clean(InvDataset invDataset) throws Exception {	
		if ( !done ) {
			List<InvDataset> children = invDataset.getDatasets();
			List<InvDataset> possibleAggregates = new ArrayList<InvDataset>();
			List<InvDataset> containerDatasets = new ArrayList<InvDataset>();
			for (Iterator dsIt = children.iterator(); dsIt.hasNext();) {
				InvDataset dataset = (InvDataset) dsIt.next();
				if ( dataset.hasAccess() ) {
					possibleAggregates.add(dataset);
					total++;
				} else {
					containerDatasets.add(dataset);
				}
			}
			if ( total > MAX_TOTAL_FILES && (total_aggregations < MIN_AGGS || total_files < MIN_FILES ) ) {
				done = true;
				System.out.println("We've looked at "+MAX_TOTAL_FILES+" files in this catalog and have fewer than "+MIN_AGGS+" aggregations and "+MIN_FILES+" files in the clean catalog... ");
				System.out.println("Consider subdividing this catalog into more managable parts.");
				return;
			}
			if ( possibleAggregates.size() > 0 && possibleAggregates.size() <= MAX_ACCESS_POINTS ) {
				System.out.println("AGGREGATES: Starting aggregate analysis for "+possibleAggregates.size()+" datasets from "+invDataset.getName()+".");
				Aggregates aggregates = new Aggregates(possibleAggregates, aggregate);
				System.out.println("AGGREGATES: Finishing aggregate analysis for "+invDataset.getName()+" datasets.");
				System.out.println("AGGREGATES: Starting to build the aggregation for "+invDataset.getName()+" datasets.");
				if ( remoteService == null ) {
					setService(aggregates.getBase());
				}
				if ( aggregates.needsAggregation() && aggregate ) {
					List<List<DatasetGridPair>> aggregations = aggregates.getAggregations();
					InvDatasetImpl parent = new InvDatasetImpl((InvDatasetImpl) invDataset);
					cleanCatalog.addDataset(parent);
					for (int i = 0; i < aggregations.size(); i++) {
						List<DatasetGridPair> agg = (List<DatasetGridPair>) aggregations.get(i);
						addAggregation(parent, invDataset, agg, i);
					}
				} else if ( aggregates.needsAggregation() && !aggregate ) {
					InvDatasetImpl parent = new InvDatasetImpl((InvDatasetImpl) invDataset);
					InvProperty property = new InvProperty("needsAggregation", "true");
					parent.addProperty(property);
					cleanCatalog.addDataset(parent);
				} 
				if ( aggregates.hasIndividualDataset() ) {
					for (Iterator ndsIt = aggregates.getIndividuals().iterator(); ndsIt.hasNext();) {
						DatasetGridPair gridDataset = (DatasetGridPair) ndsIt.next();


						if ( hasGrid(gridDataset.getGrid()) ) {
							addGridDataset(gridDataset.getDataset());
						}

					}
				}
				System.out.println("AGGREGATES: Finished building the aggregation for "+invDataset.getName()+" datasets.");
			} else {
				System.out.println("Skipping "+invDataset.getName()+" because is just too hard data sets to contemplate working with "+possibleAggregates.size()+" data sets.");
			}

			for (Iterator dsIt = containerDatasets.iterator(); dsIt.hasNext();) {
				InvDataset container = (InvDataset) dsIt.next();
				clean(container);
			}
		}
	}

	private static boolean hasGrid(InvDataset dataset) {
		Boolean has_good_grid = false;
		InvAccess access = dataset.getAccess(ServiceType.OPENDAP);

		if ( access != null ) {
			String accessUrl = access.getStandardUrlName();
			System.out.println("HASGRID: Starting grid analysis for "+accessUrl);
			try {
				NetcdfDataset nc = NetcdfDataset.openDataset(accessUrl);
				StringBuilder error = new StringBuilder();
				GridDataset gridDataset = (GridDataset) TypedDatasetFactory.open(FeatureType.GRID, nc, null, error);
				if ( gridDataset != null ) {

					has_good_grid = hasGrid(gridDataset);

				}
			} catch (IOException e) {
				System.err.println("HASGRID: Failed to open "+accessUrl+" with "+e.getLocalizedMessage());
			}
			System.out.println("Finising grid analysis for "+accessUrl);
		}
		return has_good_grid;
	}
	public static boolean hasGrid(GridDataset gridDataset) {
		boolean has_good_grid = false;
		List<GridDatatype> grids = gridDataset.getGrids();
		if ( grids != null && grids.size() > 0 ) {
			for (Iterator gridIt = grids.iterator(); gridIt.hasNext();) {
				GridDatatype grid = (GridDatatype) gridIt.next();
				GridCoordSystem gcs = grid.getCoordinateSystem();
				CoordinateAxis1D x = (CoordinateAxis1D) gcs.getXHorizAxis();
				CoordinateAxis1D y = (CoordinateAxis1D) gcs.getYHorizAxis();		
				if ( x.getSize() > 1 && y.getSize() > 1 ) {
					return true;
				}
			}
		}
		return has_good_grid;
	}
}

package gov.noaa.pmel.tmap.las.ui;

import gov.noaa.pmel.tmap.las.jdom.JDOMUtils;
import gov.noaa.pmel.tmap.las.jdom.LASBackendResponse;
import gov.noaa.pmel.tmap.las.jdom.LASConfig;
import gov.noaa.pmel.tmap.las.jdom.LASUIRequest;
import gov.noaa.pmel.tmap.las.product.server.Cache;
import gov.noaa.pmel.tmap.las.product.server.LASConfigPlugIn;
import gov.noaa.pmel.tmap.las.product.server.ServerConfigPlugIn;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.jdom.JDOMException;

public class CacheManager extends Action {
	private static Logger log = LogManager.getLogger(CacheManager.class.getName());
	/* (non-Javadoc)
	 * @see org.apache.struts.action.Action#execute(org.apache.struts.action.ActionMapping, org.apache.struts.action.ActionForm, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	public ActionForward execute(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response)
	throws Exception {
        CacheForm cacheForm = (CacheForm) form;
		log.debug("Running cache manager action.");
		ServletContext context = (ServletContext) servlet.getServletContext();
		Cache cache = (Cache) context.getAttribute(ServerConfigPlugIn.CACHE_KEY);
		String key = null;
		String clean = null;
		if ( cacheForm != null ) {
			key = cacheForm.getKey();
			clean = cacheForm.getClean();
		}
		
		synchronized(cache) {
			if ( clean != null ) {
				cache.clean();
				cacheForm.setKey(null);
				cacheForm.setClean(null);
				response.sendRedirect("CacheManager.do");
				return null;
			}
			if ( key != null ) {
				String[] keys = key.split("&");
				HashSet<String> remove = new HashSet<String>();
				for (int i = 0; i < keys.length; i++) {
					String akey = keys[i];
					for (Iterator cacheIt = cache.keySet().iterator(); cacheIt.hasNext();) {
						String filename = (String) cacheIt.next();
						if ( filename.contains(akey) ) {
							remove.add(filename);
						}
					}
				}
				for (Iterator rmIt = remove.iterator(); rmIt.hasNext();) {
					String filename = (String) rmIt.next();
					cache.removeFile(filename);
				}
				cacheForm.setKey(null);
				cacheForm.setClean(null);
				response.sendRedirect("CacheManager.do");
				return null;
			}
			LASConfig lasConfig = (LASConfig) context.getAttribute(LASConfigPlugIn.LAS_CONFIG_KEY);
			HashMap<String, HashSet<String>> datasets = new HashMap<String, HashSet<String>>();
			HashMap<String, HashSet<String>> dataset_keys = new HashMap<String, HashSet<String>>();
			HashMap<String, ArrayList<String>> keyToDatasetMap = new HashMap<String, ArrayList<String>>();
			for (Iterator cacheIt = cache.keySet().iterator(); cacheIt.hasNext();) {
				String filename = (String) cacheIt.next();
				if ( filename.contains("_request") ) {
					LASUIRequest las_request = new LASUIRequest();

					JDOMUtils.XML2JDOM(new File(filename), las_request);
					ArrayList<String> dataset_ids =  las_request.getDatasetIDs();
					String bkey = filename.substring(filename.indexOf("_request")-32,filename.indexOf("_request"));
					if ( dataset_ids.size() > 0 ) {
						keyToDatasetMap.put(bkey, dataset_ids);
					} else {
						dataset_ids.add("Map Widget Basemap for cache key " + bkey);
						keyToDatasetMap.put(bkey, dataset_ids);
					}

				}
			}
			HashSet<String> ckeys = new HashSet<String>();
			for (Iterator keyIt = keyToDatasetMap.keySet().iterator(); keyIt.hasNext();) {
				String bkey = (String) keyIt.next();
				HashSet<String> files = new HashSet<String>();
				for (Iterator fileIt = cache.keySet().iterator(); fileIt.hasNext();) {
					String name = (String) fileIt.next();
					if ( name.contains(bkey) ) {
						log.debug("name = "+name+" key="+bkey);
						if ( !name.endsWith(".rss") ) {
						    files.add(name);
						}
						if ( name.contains("_response") ) {


							LASBackendResponse las_response = new LASBackendResponse();

							try {
								JDOMUtils.XML2JDOM(new File(name), las_response);
							} catch (IOException e) {
								// Don't care.
							} catch (JDOMException e) {
								// Don't care.
							}
							
							ArrayList<String> f = las_response.getResultsAsFiles();
							String template = f.get(0);
							files.addAll(f);
							
							// Get the keys and add a response file for each key.
							ckeys = las_response.getCacheKeys();
							if ( keyToDatasetMap.get(bkey).size() > 0 ) {	
								for (Iterator dsidIt = keyToDatasetMap.get(bkey).iterator(); dsidIt.hasNext();) {
									String dsid = (String) dsidIt.next();
									HashSet<String> dskeys;
									if ( dataset_keys.containsKey(dsid)) {
										dskeys = dataset_keys.get(dsid);
									} else {
										dskeys = new HashSet<String>();
									}
									dskeys.addAll(ckeys);
									dataset_keys.put(dsid, dskeys);
								}
							}
							for (Iterator ckIt = ckeys.iterator(); ckIt.hasNext();) {
								String ckey = (String) ckIt.next();
								String fn = template.substring(0, template.lastIndexOf("/"))+File.separator+ckey+"_response.xml";
								files.add(fn);
							}

						}
					} 
					if ( keyToDatasetMap.get(bkey).size() > 0 ) {	
						for (Iterator dsidIt = keyToDatasetMap.get(bkey).iterator(); dsidIt.hasNext();) {
							String dsid = (String) dsidIt.next();
							if ( datasets.containsKey(dsid) ) {
								HashSet<String> stored_files = datasets.get(dsid);
								stored_files.addAll(files);
								datasets.put(dsid, stored_files);
							} else {
								datasets.put(dsid, files);
							}
						}
					}
				}
				request.setAttribute("datasets", datasets);
			}
            request.setAttribute("dataset_keys", dataset_keys);
            request.setAttribute("max_bytes", cache.getMaxBytes());
            request.setAttribute("max_files", cache.getCacheSize());
            request.setAttribute("size", cache.getCurrentBytes());
            request.setAttribute("numfiles", cache.size());
            
            return mapping.findForward("cache");
		}
	}
}

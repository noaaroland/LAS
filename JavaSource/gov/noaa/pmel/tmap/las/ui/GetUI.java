/*
 * Generated by MyEclipse Struts
 * Template path: templates/java/JavaClass.vtl
 */
package gov.noaa.pmel.tmap.las.ui;

import gov.noaa.pmel.tmap.addxml.ADDXMLProcessor;
import gov.noaa.pmel.tmap.addxml.AxisBean;
import gov.noaa.pmel.tmap.addxml.DatasetBean;
import gov.noaa.pmel.tmap.addxml.DatasetsGridsAxesBean;
import gov.noaa.pmel.tmap.addxml.GridBean;
import gov.noaa.pmel.tmap.exception.LASException;
import gov.noaa.pmel.tmap.jdom.LASDocument;
import gov.noaa.pmel.tmap.las.jdom.JDOMUtils;
import gov.noaa.pmel.tmap.las.jdom.LASBackendResponse;
import gov.noaa.pmel.tmap.las.jdom.LASConfig;
import gov.noaa.pmel.tmap.las.jdom.ServerConfig;
import gov.noaa.pmel.tmap.las.product.server.InitThread;
import gov.noaa.pmel.tmap.las.product.server.LASConfigPlugIn;
import gov.noaa.pmel.tmap.las.util.Constants;
import gov.noaa.pmel.tmap.las.util.Dataset;
import gov.noaa.pmel.tmap.las.util.Variable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import thredds.catalog.InvCatalog;
import thredds.catalog.InvCatalogFactory;

/** 
 * MyEclipse Struts
 * Creation date: 01-08-2007
 * 
 * XDoclet definition:
 * @struts.action validate="true"
 */
public class GetUI extends ConfigService {
    /*
     * Generated Methods
     */

    /** 
     * Method execute
     * @param mapping
     * @param form
     * @param request
     * @param response
     * @return ActionForward
     */
    private static Logger log = LogManager.getLogger(GetUI.class.getName());

    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {    
        ServletContext context = (ServletContext) servlet.getServletContext();
        String query = request.getQueryString();
        LASConfig lasConfig = (LASConfig)servlet.getServletContext().getAttribute(LASConfigPlugIn.LAS_CONFIG_KEY);
        ServerConfig serverConfig = (ServerConfig) servlet.getServletContext().getAttribute(LASConfigPlugIn.SERVER_CONFIG_KEY);
        String[] cat_ids = request.getParameterValues("catid");
        if ( cat_ids != null ) {
            
            try {
                
                int found = 0;
                for ( int i = 0; i < cat_ids.length; i++ ) {
                    //TODO session management probably by adding the JSESSIONID to the data set.
                    String key_id = lasConfig.addDataset(cat_ids[i]);
                    if ( key_id != null ) {
                        found++;
                        if (!key_id.equals(cat_ids[i]) ) {
                            cat_ids[i] = key_id;
                        }
                    }
                }
                if ( found > 0 ) {
                    lasConfig.convertToSeven(true);
                    lasConfig.mergeProperites();
                    lasConfig.addIntervalsAndPoints();    
                    lasConfig.addGridType();
                    //                String fds_base = serverConfig.getFTDSBase();
                    //                String fds_dir = serverConfig.getFTDSDir();
                    //                lasConfig.addFDS(fds_base, fds_dir);
                } else {
                    throw new Exception("Search did not return any information for the requested data set.");
                }
            } catch (Exception e) {
                String las_message = "Unable to initialize UI from the catid values provided.";
                LASBackendResponse lasBackendResponse = new LASBackendResponse();
                lasBackendResponse.setError("las_message", las_message);
                lasBackendResponse.addError("exception_message", e.toString());
                request.setAttribute("las_response", lasBackendResponse);
                StackTraceElement[] trace =  e.getStackTrace();
                log.error(las_message);
                log.error(e.toString());
                if ( trace.length > 0 ) {
                    log.error(trace[0].toString());
                }           
                return mapping.findForward("error");
            }
            request.getSession().setAttribute("catid", cat_ids);
            
            String auto = request.getParameter("auto");
            if ( auto != null && auto.equals("true") ) {
                Dataset dataset = lasConfig.getFullDataset(cat_ids[0]);
                List<Variable> vars = dataset.getVariables();
                if ( vars != null && vars.size() > 0 ) {
                    query = query + "&dsid="+dataset.getID()+"&varid="+vars.get(0).getID();
                }
            }
        }
        // forward to the UI
        response.sendRedirect("UI.vm?"+query);
        return null;
        //return new ActionForward("/UI.vm?"+query, true);

    }

}

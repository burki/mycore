/*
 * $Revision$ $Date$
 * 
 * This file is part of *** M y C o R e *** See http://www.mycore.de/ for
 * details.
 * 
 * This program is free software; you can use it, redistribute it and / or
 * modify it under the terms of the GNU General Public License (GPL) as
 * published by the Free Software Foundation; either version 2 of the License or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program, in a file called gpl.txt or license.txt. If not, write to the
 * Free Software Foundation Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307 USA
 */

package org.mycore.oai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.mycore.common.MCRConfiguration;
import org.mycore.frontend.servlets.MCRServlet;
import org.mycore.frontend.servlets.MCRServletJob;

/**
 * Implements an OAI-PMH 2.0 Data Provider as a servlet.
 * 
 * @author Frank L\u00fctzenkirchen
 */
public class MCROAIDataProvider extends MCRServlet {
    private static final long serialVersionUID = 1L;

    protected final static Logger LOGGER = Logger.getLogger(MCROAIDataProvider.class);

    private MCROAIAdapter adapter;

    private String myBaseURL;

    private String prefix;

    private String repositoryName;

    private String repositoryIdentifier;

    private String adminEmail;

    /** the sample id */
    private String recordSampleID;

    /**
     * List of metadata formats supported by this data provider instance.
     */
    private List<MCRMetadataFormat> metadataFormats = new ArrayList<MCRMetadataFormat>();

    @SuppressWarnings("unchecked")
    protected void doGetPost(MCRServletJob job) throws Exception {
        HttpServletRequest request = job.getRequest();
        if (myBaseURL == null)
            myBaseURL = getBaseURL() + request.getServletPath().substring(1);

        logRequest(request);

        String[] verb = request.getParameterValues("verb");
        MCRVerbHandler handler = null;

        if ((verb == null) || (verb.length == 0))
            handler = new MCRBadVerbHandler(this, "Missing required argument 'verb'");
        else if (verb.length > 1)
            handler = new MCRBadVerbHandler(this, "Multiple 'verb' arguments in request");
        else if (verb[0].trim().length() == 0)
            handler = new MCRBadVerbHandler(this, "Required argument 'verb' is empty");
        else if (MCRIdentifyHandler.VERB.equals(verb[0]))
            handler = new MCRIdentifyHandler(this);
        else if (MCRGetRecordHandler.VERB.equals(verb[0]))
            handler = new MCRGetRecordHandler(this);
        else if (MCRListMetadataFormatsHandler.VERB.equals(verb[0]))
            handler = new MCRListMetadataFormatsHandler(this);
        else if (MCRListSetsHandler.VERB.equals(verb[0]))
            handler = new MCRListSetsHandler(this);
        else if (MCRListRecordsHandler.VERB.equals(verb[0]))
            handler = new MCRListRecordsHandler(this);
        else if (MCRListIdentifiersHandler.VERB.equals(verb[0]))
            handler = new MCRListIdentifiersHandler(this);
        else
            handler = new MCRBadVerbHandler(this, "Bad verb: " + verb[0]);

        Document response = handler.handle(request.getParameterMap());

        job.getResponse().setContentType("text/xml; charset=UTF-8");
        XMLOutputter xout = new XMLOutputter();
        xout.setFormat(Format.getPrettyFormat().setEncoding("UTF-8"));
        xout.output(response, job.getResponse().getOutputStream());
    }

    /**
     * @param req
     */
    @SuppressWarnings("rawtypes")
    protected void logRequest(HttpServletRequest req) {
        StringBuffer log = new StringBuffer(this.getServletName());
        for (Iterator it = req.getParameterMap().keySet().iterator(); it.hasNext();) {
            String name = (String) it.next();
            for (String value : req.getParameterValues(name))
                log.append(" ").append(name).append("=").append(value);
        }
        LOGGER.info(log.toString());
    }

    public void init() throws ServletException {
        super.init();
        MCRConfiguration config = MCRConfiguration.instance();
        prefix = "MCR.OAIDataProvider." + getServletName() + ".";
        adapter = (MCROAIAdapter) (config.getInstanceOf(prefix + "Adapter", MCROAIAdapterMyCoRe.class.getName()));
        adapter.init(prefix);

        repositoryName = config.getString(prefix + "RepositoryName");
        repositoryIdentifier = config.getString(prefix + "RepositoryIdentifier");
        adminEmail = config.getString(prefix + "AdminEmail", config.getString("MCR.Mail.Address"));
        recordSampleID = config.getString(prefix + "RecordSampleID");
        adapter.setDeletedRecordPolicy(config.getString(prefix + "DeletedRecord", MCROAIConstants.DELETED_RECORD_POLICY_TRANSIENT));

        String formats = config.getString(prefix + "MetadataFormats");
        StringTokenizer st = new StringTokenizer(formats, ", ");
        while (st.hasMoreTokens()) {
            getMetadataFormats().add(MCRMetadataFormat.getFormat(st.nextToken()));
        }
    }

    /** Returns the underlying {@link MCROAIAdapter} */
    MCROAIAdapter getAdapter() {
        return adapter;
    }

    /**
     * Returns the base URL of this data provider instance
     */
    String getOAIBaseURL() {
        return myBaseURL;
    }

    String getPrefix() {
        return prefix;
    }

    /** Returns the name of the repository */
    public String getRepositoryName() {
        return repositoryName;
    }

    /** Returns the id of the oai repository */
    public String getRepositoryIdentifier() {
        return repositoryIdentifier;
    }

    /** Returns a samle record id */
    public String getRecordSampleID() {
        return recordSampleID;
    }

    /**
     * Returns the policy for deleted items
     * 
     * @return one of no, transient or persistent
     */
    public String getDeletedRecordPolicy() {
        return getAdapter().getDeletedRecordPolicy();
    }

    /** Returns the admin email adress */
    public String getAdminEmail() {
        return adminEmail;
    }

    /**
     * Returns the metadata formats supported by this data provider instance.
     * For each instance, a configuration property lists the prefixes of all
     * supported formats, for example
     * MCR.OAIDataProvider.OAI.MetadataFormats=oai_dc Each metadata format must
     * be globally configured with its prefix, schema and namespace, for example
     * MCR.OAIDataProvider.MetadataFormat.oai_dc.Schema=http://www.openarchives.
     * org/OAI/2.0/oai_dc.xsd
     * MCR.OAIDataProvider.MetadataFormat.oai_dc.Namespace
     * =http://www.openarchives.org/OAI/2.0/oai_dc/
     * 
     * @see MCRMetadataFormat
     */
    List<MCRMetadataFormat> getMetadataFormats() {
        return metadataFormats;
    }
}

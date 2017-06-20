/*
 * $RCSfile$
 * $Revision: 19696 $ $Date: 2011-01-04 13:45:05 +0100 (Di, 04 Jan 2011) $
 *
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * This program is free software; you can use it, redistribute it
 * and / or modify it under the terms of the GNU General Public License
 * (GPL) as published by the Free Software Foundation; either version 2
 * of the License or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program, in a file called gpl.txt or license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 */
package org.mycore.restapi.v1.utils;

import static org.mycore.access.MCRAccessManager.PERMISSION_DELETE;
import static org.mycore.access.MCRAccessManager.PERMISSION_WRITE;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Signature;
import java.util.Base64;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.jdom2.Document;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.mycore.access.MCRAccessException;
import org.mycore.access.MCRAccessManager;
import org.mycore.common.MCRPersistenceException;
import org.mycore.common.MCRSession;
import org.mycore.common.MCRSessionMgr;
import org.mycore.common.MCRUserInformation;
import org.mycore.common.config.MCRConfiguration;
import org.mycore.datamodel.ifs.MCRDirectory;
import org.mycore.datamodel.ifs.MCRFileImportExport;
import org.mycore.datamodel.metadata.MCRDerivate;
import org.mycore.datamodel.metadata.MCRMetaIFS;
import org.mycore.datamodel.metadata.MCRMetaLinkID;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.datamodel.niofs.MCRPath;
import org.mycore.datamodel.niofs.utils.MCRRecursiveDeleter;
import org.mycore.frontend.cli.MCRObjectCommands;
import org.mycore.frontend.servlets.MCRServlet;
import org.mycore.restapi.v1.errors.MCRRestAPIError;
import org.mycore.restapi.v1.errors.MCRRestAPIException;
import org.mycore.user2.MCRUserManager;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;

public class MCRRestAPIUploadHelper {
    private static final Logger LOGGER = LogManager.getLogger(MCRRestAPIUploadHelper.class);

    public static final String FORMAT_XML = "xml";

    private static java.nio.file.Path UPLOAD_DIR = Paths
        .get(MCRConfiguration.instance().getString("MCR.RestAPI.v1.Upload.Directory"));
    static {
        if (!Files.exists(UPLOAD_DIR)) {
            try {
                Files.createDirectories(UPLOAD_DIR);
            } catch (IOException e) {
                LOGGER.error(e);
            }
        }
    }

    /**
     * uploads a mycore mobject    
     * based upon:    
     * http://puspendu.wordpress.com/2012/08/23/restful-webservice-file-upload-with-jersey/
     */
    public static Response uploadObject(UriInfo info, HttpServletRequest request, InputStream uploadedInputStream,
        FormDataContentDisposition fileDetails) {
        try {
            if (MCRRestAPIUtil.checkWriteAccessForIP(request)) {
                SignedJWT signedJWT = MCRJSONWebTokenUtil.retrieveAuthenticationToken(request);
                java.nio.file.Path fXML = null;
                try (MCRJPATransactionWrapper mtw = new MCRJPATransactionWrapper()) {
                    SAXBuilder sb = new SAXBuilder();
                    Document docOut = sb.build(uploadedInputStream);

                    MCRObjectID mcrID = MCRObjectID.getInstance(docOut.getRootElement().getAttributeValue("ID"));
                    if (mcrID.getNumberAsInteger() == 0) {
                        mcrID = MCRObjectID.getNextFreeId(mcrID.getBase());
                    }

                    fXML = UPLOAD_DIR.resolve(mcrID.toString() + ".xml");

                    docOut.getRootElement().setAttribute("ID", mcrID.toString());
                    docOut.getRootElement().setAttribute("label", mcrID.toString());
                    XMLOutputter xmlOut = new XMLOutputter(Format.getPrettyFormat());
                    try (BufferedWriter bw = Files.newBufferedWriter(fXML, StandardCharsets.UTF_8)) {
                        xmlOut.output(docOut, bw);
                    }

                    MCRSession mcrSession = MCRSessionMgr.getCurrentSession();
                    MCRUserInformation currentUser = mcrSession.getUserInformation();
                    MCRUserInformation apiUser = MCRUserManager
                        .getUser(MCRJSONWebTokenUtil.retrieveUsernameFromAuthenticationToken(signedJWT));
                    mcrSession.setUserInformation(apiUser);
                    MCRObjectCommands.updateFromFile(fXML.toString(), false); // handles "create" as well
                    mcrSession.setUserInformation(currentUser);

                    return Response.created(info.getBaseUriBuilder().path("v1/objects/" + mcrID.toString()).build())
                        .type("application/xml; charset=UTF-8")
                        .header("Authorization", "Bearer " + MCRJSONWebTokenUtil.createJWT(signedJWT).serialize())
                        .build();

                } catch (Exception e) {
                    LOGGER.error("Unable to Upload file: " + String.valueOf(fXML), e);
                    throw new MCRRestAPIException(
                        MCRRestAPIError.create(Status.BAD_REQUEST, MCRRestAPIError.CODE_WRONG_PARAMETER,
                            "Unable to Upload file: " + String.valueOf(fXML), e.getMessage()));
                } finally {
                    if (fXML != null) {
                        try {
                            Files.delete(fXML);
                        } catch (IOException e) {
                            LOGGER.error("Unable to delete temporary workflow file: " + String.valueOf(fXML), e);
                        }
                    }
                }
            }

        } catch (MCRRestAPIException e) {
            return MCRRestAPIError.createHttpResponseFromErrorList(e.getErrors());
        }
        return MCRRestAPIError.create(Status.INTERNAL_SERVER_ERROR, MCRRestAPIError.CODE_INTERNAL_ERROR,
            "Could not upload the file", null).createHttpResponse();
    }

    public static Response uploadDerivate(UriInfo info, HttpServletRequest request, String pathParamMcrObjID,
        String formParamlabel) {
        Response response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
        try {
            if (MCRRestAPIUtil.checkWriteAccessForIP(request)) {
                SignedJWT signedJWT = MCRJSONWebTokenUtil.retrieveAuthenticationToken(request);
                //  File fXML = null;
                MCRObjectID mcrObjID = MCRObjectID.getInstance(pathParamMcrObjID);

                try (MCRJPATransactionWrapper mtw = new MCRJPATransactionWrapper()) {
                    MCRSession session = MCRServlet.getSession(request);
                    MCRUserInformation currentUser = session.getUserInformation();
                    MCRUserInformation apiUser = MCRUserManager
                        .getUser(MCRJSONWebTokenUtil.retrieveUsernameFromAuthenticationToken(signedJWT));
                    session.setUserInformation(apiUser);

                    MCRObject mcrObj = MCRMetadataManager.retrieveMCRObject(mcrObjID);
                    MCRObjectID derID = null;
                    for (MCRMetaLinkID derLink : mcrObj.getStructure().getDerivates()) {
                        if (formParamlabel.equals(derLink.getXLinkLabel())
                            || formParamlabel.equals(derLink.getXLinkTitle())) {
                            derID = derLink.getXLinkHrefID();
                        }
                    }

                    if (derID == null) {
                        derID = MCRObjectID.getNextFreeId(mcrObjID.getProjectId() + "_derivate");
                        MCRDerivate mcrDerivate = new MCRDerivate();
                        mcrDerivate.setLabel(formParamlabel);
                        mcrDerivate.setId(derID);
                        mcrDerivate.setSchema("datamodel-derivate.xsd");
                        mcrDerivate.getDerivate().setLinkMeta(new MCRMetaLinkID("linkmeta", mcrObjID, null, null));
                        mcrDerivate.getDerivate()
                            .setInternals(new MCRMetaIFS("internal", UPLOAD_DIR.resolve(derID.toString()).toString()));

                        MCRMetadataManager.create(mcrDerivate);
                        MCRMetadataManager.addOrUpdateDerivateToObject(mcrObjID,
                            new MCRMetaLinkID("derobject", derID, null, formParamlabel));
                    }

                    response = Response
                        .created(info.getBaseUriBuilder()
                            .path("v1/objects/" + mcrObjID.toString() + "/derivates/" + derID.toString()).build())
                        .type("application/xml; charset=UTF-8")
                        .header("Authorization", "Bearer " + MCRJSONWebTokenUtil.createJWT(signedJWT).serialize())
                        .build();
                    session.setUserInformation(currentUser);
                } catch (Exception e) {
                    LOGGER.error("Exeption while uploading derivate", e);
                }
            }
        } catch (MCRRestAPIException e) {
            response = MCRRestAPIError.createHttpResponseFromErrorList(e.getErrors());
        }
        return response;
    }

    public static Response uploadFile(UriInfo info, HttpServletRequest request, String pathParamMcrObjID,
        String pathParamMcrDerID, InputStream uploadedInputStream, FormDataContentDisposition fileDetails,
        String formParamPath, boolean formParamMaindoc, boolean formParamUnzip, String formParamMD5,
        Long formParamSize) {

        Response response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
        try {
            if (MCRRestAPIUtil.checkWriteAccessForIP(request)) {
                SignedJWT signedJWT = MCRJSONWebTokenUtil.retrieveAuthenticationToken(request);
                SortedMap<String, String> parameter = new TreeMap<>();
                parameter.put("mcrObjectID", pathParamMcrObjID);
                parameter.put("mcrDerivateID", pathParamMcrDerID);
                parameter.put("path", formParamPath);
                parameter.put("maindoc", Boolean.toString(formParamMaindoc));
                parameter.put("unzip", Boolean.toString(formParamUnzip));
                parameter.put("md5", formParamMD5);
                parameter.put("size", Long.toString(formParamSize));

                String base64Signature = request.getHeader("X-MyCoRe-RestAPI-Signature");
                if (base64Signature == null) {
                    //ToDo error handling
                }
                if (verifyPropertiesWithSignature(parameter, base64Signature,
                    MCRJSONWebTokenUtil.retrievePublicKeyFromAuthenticationToken(signedJWT))) {
                    try (MCRJPATransactionWrapper mtw = new MCRJPATransactionWrapper()) {
                        //MCRSession session = MCRServlet.getSession(request);
                        MCRSession session = MCRSessionMgr.getCurrentSession();
                        MCRUserInformation currentUser = session.getUserInformation();

                        MCRUserInformation apiUser = MCRUserManager
                            .getUser(MCRJSONWebTokenUtil.retrieveUsernameFromAuthenticationToken(signedJWT));
                        session.setUserInformation(apiUser);
                        MCRObjectID objID = MCRObjectID.getInstance(pathParamMcrObjID);
                        MCRObjectID derID = MCRObjectID.getInstance(pathParamMcrDerID);

                        //TODO check against derivate ACLs -  Access Strategy in Skeleton does not work
                        //if (MCRAccessManager.getAccessImpl().checkPermission(derID.toString(), PERMISSION_WRITE)) {
                        MCRDerivate der = MCRMetadataManager.retrieveMCRDerivate(derID);

                        java.nio.file.Path derDir = null;

                        String path = null;
                        if (der.getOwnerID().equals(objID)) {
                            try {
                                derDir = UPLOAD_DIR.resolve(derID.toString());
                                if (Files.exists(derDir)) {
                                    Files.walkFileTree(derDir, MCRRecursiveDeleter.instance());
                                }
                                path = formParamPath.replace("\\", "/").replace("../", "");

                                MCRDirectory difs = MCRDirectory.getRootDirectory(derID.toString());
                                if (difs == null) {
                                    difs = new MCRDirectory(derID.toString());
                                }

                                der.getDerivate().getInternals().setIFSID(difs.getID());
                                der.getDerivate().getInternals().setSourcePath(derDir.toString());

                                if (formParamUnzip) {
                                    String maindoc = null;
                                    try (ZipInputStream zis = new ZipInputStream(
                                        new BufferedInputStream(uploadedInputStream))) {
                                        ZipEntry entry;
                                        while ((entry = zis.getNextEntry()) != null) {
                                            LOGGER.debug("Unzipping: " + entry.getName());
                                            java.nio.file.Path target = derDir.resolve(entry.getName());
                                            Files.createDirectories(target.getParent());
                                            Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                                            if (maindoc == null && !entry.isDirectory()) {
                                                maindoc = entry.getName();
                                            }
                                        }
                                    } catch (IOException e) {
                                        LOGGER.error(e);
                                    }

                                    MCRFileImportExport.importFiles(derDir.toFile(), difs);

                                    if (formParamMaindoc) {
                                        der.getDerivate().getInternals().setMainDoc(maindoc);
                                    }
                                } else {
                                    java.nio.file.Path saveFile = derDir.resolve(path);
                                    Files.createDirectories(saveFile.getParent());
                                    Files.copy(uploadedInputStream, saveFile, StandardCopyOption.REPLACE_EXISTING);
                                    //delete old file
                                    MCRFileImportExport.importFiles(derDir.toFile(), difs);
                                    if (formParamMaindoc) {
                                        der.getDerivate().getInternals().setMainDoc(path);
                                    }
                                }

                                MCRMetadataManager.update(der);
                                Files.walkFileTree(derDir, MCRRecursiveDeleter.instance());
                            } catch (IOException | MCRAccessException e) {
                                LOGGER.error(e);
                                throw new MCRRestAPIException(MCRRestAPIError.create(Status.INTERNAL_SERVER_ERROR,
                                    MCRRestAPIError.CODE_INTERNAL_ERROR, "Internal error", e.getMessage()));
                            }
                        }
                        session.setUserInformation(currentUser);
                        response = Response
                            .created(
                                info.getBaseUriBuilder()
                                    .path("v1/objects/" + objID.toString() + "/derivates/" + derID.toString()
                                        + "/contents")
                                    .build())
                            .type("application/xml; charset=UTF-8")
                            .header("Authorization", "Bearer " + MCRJSONWebTokenUtil.createJWT(signedJWT).serialize())
                            .build();

                    }

                } else {
                    //TODO error handling
                }
            }
        } catch (MCRRestAPIException e) {
            response = MCRRestAPIError.createHttpResponseFromErrorList(e.getErrors());
        }
        return response;
    }

    public static Response deleteAllFiles(UriInfo info, HttpServletRequest request, String pathParamMcrObjID,
        String pathParamMcrDerID) {

        Response response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
        try {
            if (MCRRestAPIUtil.checkWriteAccessForIP(request)) {
                SignedJWT signedJWT = MCRJSONWebTokenUtil.retrieveAuthenticationToken(request);
                SortedMap<String, String> parameter = new TreeMap<>();
                parameter.put("mcrObjectID", pathParamMcrObjID);
                parameter.put("mcrDerivateID", pathParamMcrDerID);

                String base64Signature = request.getHeader("X-MyCoRe-RestAPI-Signature");
                if (base64Signature == null) {
                    //ToDo error handling
                }
                if (verifyPropertiesWithSignature(parameter, base64Signature,
                    MCRJSONWebTokenUtil.retrievePublicKeyFromAuthenticationToken(signedJWT))) {
                    try (MCRJPATransactionWrapper mtw = new MCRJPATransactionWrapper()) {
                        //MCRSession session = MCRServlet.getSession(request);
                        MCRSession session = MCRSessionMgr.getCurrentSession();
                        MCRUserInformation currentUser = session.getUserInformation();
                        MCRUserInformation apiUser = MCRUserManager
                            .getUser(MCRJSONWebTokenUtil.retrieveUsernameFromAuthenticationToken(signedJWT));
                        session.setUserInformation(apiUser);
                        MCRObjectID objID = MCRObjectID.getInstance(pathParamMcrObjID);
                        MCRObjectID derID = MCRObjectID.getInstance(pathParamMcrDerID);

                        //MCRAccessManager.checkPermission
                        //(uses CACHE, which seems to be dirty from other calls and cannot be deleted)????
                        if (MCRAccessManager.getAccessImpl().checkPermission(derID.toString(), PERMISSION_WRITE)) {
                            MCRDerivate der = MCRMetadataManager.retrieveMCRDerivate(derID);

                            final MCRPath rootPath = MCRPath.getPath(der.getId().toString(), "/");
                            try {
                                Files.walkFileTree(rootPath, MCRRecursiveDeleter.instance());
                                Files.createDirectory(rootPath);
                            } catch (IOException e) {
                                LOGGER.error(e);
                            }
                        }

                        session.setUserInformation(currentUser);
                        response = Response
                            .created(
                                info.getBaseUriBuilder()
                                    .path("v1/objects/" + objID.toString() + "/derivates/" + derID.toString()
                                        + "/contents")
                                    .build())
                            .type("application/xml; charset=UTF-8")
                            .header("Authorization", "Bearer " + MCRJSONWebTokenUtil.createJWT(signedJWT).serialize())
                            .build();
                    }
                } else {
                    //TODO Error handling
                }
            }
        } catch (MCRRestAPIException e) {
            response = MCRRestAPIError.createHttpResponseFromErrorList(e.getErrors());
        }
        return response;
    }

    public static Response deleteDerivate(UriInfo info, HttpServletRequest request, String pathParamMcrObjID,
        String pathParamMcrDerID) {

        Response response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
        try {
            if (MCRRestAPIUtil.checkWriteAccessForIP(request)) {
                SignedJWT signedJWT = MCRJSONWebTokenUtil.retrieveAuthenticationToken(request);
                SortedMap<String, String> parameter = new TreeMap<>();
                parameter.put("mcrObjectID", pathParamMcrObjID);
                parameter.put("mcrDerivateID", pathParamMcrDerID);

                String base64Signature = request.getHeader("X-MyCoRe-RestAPI-Signature");
                if (base64Signature == null) {
                    //ToDo error handling
                }
                try (MCRJPATransactionWrapper mtw = new MCRJPATransactionWrapper()) {
                    //MCRSession session = MCRServlet.getSession(request);
                    MCRSession session = MCRSessionMgr.getCurrentSession();
                    MCRUserInformation currentUser = session.getUserInformation();
                    session.setUserInformation(
                        MCRUserManager.getUser(MCRJSONWebTokenUtil.retrieveUsernameFromAuthenticationToken(signedJWT)));
                    MCRObjectID objID = MCRObjectID.getInstance(pathParamMcrObjID);
                    MCRObjectID derID = MCRObjectID.getInstance(pathParamMcrDerID);

                    //MCRAccessManager.checkPermission(uses CACHE, which seems to be dirty from other calls and cannot be deleted)????
                    if (MCRAccessManager.getAccessImpl().checkPermission(derID.toString(), PERMISSION_DELETE)) {
                        try {
                            MCRMetadataManager.deleteMCRDerivate(derID);
                        } catch (MCRPersistenceException pe) {
                            //dir does not exist - do nothing
                        } catch (MCRAccessException e) {
                            LOGGER.error(e);
                        }
                    }
                    session.setUserInformation(currentUser);
                    response = Response
                        .created(info.getBaseUriBuilder().path("v1/objects/" + objID.toString() + "/derivates").build())
                        .type("application/xml; charset=UTF-8")
                        .header("Authorization", "Bearer " + MCRJSONWebTokenUtil.createJWT(signedJWT).serialize())
                        .build();
                }
            }
        } catch (MCRRestAPIException e) {
            response = MCRRestAPIError.createHttpResponseFromErrorList(e.getErrors());
        }
        return response;
    }

    public static String generateMessagesFromProperties(SortedMap<String, String> data) {
        StringWriter sw = new StringWriter();
        sw.append("{");
        for (String key : data.keySet()) {
            sw.append("\"").append(key).append("\"").append(":").append("\"").append(data.get(key)).append("\"")
                .append(",");
        }
        String result = sw.toString();
        if (result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        result = result + "}";

        return result;
    }

    public static boolean verifyPropertiesWithSignature(SortedMap<String, String> data, String base64Signature,
        JWK jwk) {
        try {
            String message = generateMessagesFromProperties(data);

            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initVerify(((RSAKey) jwk).toRSAPublicKey());
            signature.update(message.getBytes(StandardCharsets.ISO_8859_1));

            boolean x = signature.verify(Base64.getDecoder().decode(base64Signature));
            return x;

        } catch (Exception e) {
            LOGGER.error(e);
        }
        return false;
    }
}
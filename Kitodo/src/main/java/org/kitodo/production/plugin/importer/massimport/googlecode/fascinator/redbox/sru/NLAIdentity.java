/*
 * The Fascinator - ReDBox/Mint SRU Client - NLA Identity Copyright (C) 2012
 * Queensland Cyber Infrastructure Foundation (http://www.qcif.edu.au/)
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kitodo.production.plugin.importer.massimport.googlecode.fascinator.redbox.sru;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Element;
import org.dom4j.Node;

/**
 * <p>
 * A basic wrapper for handling EAC-CPF formatted identities that return for the
 * National Library of Australia. This is neither a complete EAC-CPF handling
 * class, nor a complete implementation of NLA identities. It is just a utility
 * for access the things ReDBox/Mint cares about in common node.
 * </p>
 *
 * @author Greg Pendlebury
 */
public class NLAIdentity {
    /** Logging. **/
    private static Logger logger = LogManager.getLogger(NLAIdentity.class);

    /** DOM4J Node for this person. **/
    private Node eac;

    /** Properties we extract. **/
    private String nlaId;
    private String displayName;
    private String firstName;
    private String surname;
    private String institution;
    private List<Map<String, String>> knownIds;

    private static final String DISPLAY_NAME = "displayName";
    private static final String INSTITUTION = "institution";

    /**
     * <p>
     * Default Constructor. Extract some basic information.
     * </p>
     *
     * @param node
     *            searchResponse A parsed DOM4J Document
     * @throws SRUException
     *             If any of the XML structure does not look like expected
     */
    public NLAIdentity(Node node) throws SRUException {
        eac = node;

        // Identity
        @SuppressWarnings("unchecked")
        List<Node> otherIds = eac.selectNodes("eac:eac-cpf/eac:control/eac:otherRecordId");
        for (Node idNode : otherIds) {
            String otherId = idNode.getText();
            if (otherId.startsWith("http://nla.gov.au")) {
                nlaId = otherId;
            }
        }
        if (nlaId == null) {
            throw new SRUException("Error processing Identity; Cannot find ID");
        }

        knownIds = getSourceIdentities();

        // Cosmetically we want to use the first row (should be the longest
        // top-level name we found)
        firstName = knownIds.get(0).get("firstName");
        surname = knownIds.get(0).get("surname");
        displayName = knownIds.get(0).get(DISPLAY_NAME);
        // For institution we want the first one we find that isn't NLA or
        // Libraries Australia
        for (Map<String, String> id : knownIds) {
            if (institution == null
                    // But we'll settle for those in a pinch
                    || "National Library of Australia Party Infrastructure".equals(institution)
                    || "Libraries Australia".equals(institution)) {
                institution = id.get(INSTITUTION);
            }
        }
    }

    private List<Map<String, String>> getSourceIdentities() {
        List<Map<String, String>> returnList = new ArrayList<>();

        // Top level institution
        Map<String, String> idMap = new HashMap<>();
        Node institutionNode = eac.selectSingleNode("eac:eac-cpf/eac:control/eac:maintenanceAgency/eac:agencyName");
        String institutionString = institutionNode.getText();
        // Top level name
        Node nlaNamesNode = eac.selectSingleNode("eac:eac-cpf/eac:cpfDescription/eac:identity");
        // Get all the names this ID lists
        List<Map<String, String>> nameList = getNames(nlaNamesNode);
        for (Map<String, String> name : nameList) {
            // Only use the longest top-level name for display purposes
            String oldDisplayName = idMap.get(DISPLAY_NAME);
            String thisDisplayName = name.get(DISPLAY_NAME);
            if (oldDisplayName == null
                    || (thisDisplayName != null && thisDisplayName.length() > oldDisplayName.length())) {
                // Clear any old data
                idMap.clear();
                // Store this ID
                idMap.putAll(name);
                idMap.put(INSTITUTION, institutionString);
            }
        }
        // And add to the list
        returnList.add(idMap);

        // All name entities from contributing institutions
        @SuppressWarnings("unchecked")
        List<Node> sourceIdentities = eac.selectNodes("eac:eac-cpf/eac:cpfDescription//eac:eac-cpf");
        for (Node identity : sourceIdentities) {
            // Institution for this ID
            institutionNode = identity.selectSingleNode("*//eac:maintenanceAgency/eac:agencyName");

            if (Objects.nonNull(institutionNode)) {
                institutionString = institutionNode.getText();

                // Any names for this ID
                @SuppressWarnings("unchecked")
                List<Node> idNodes = identity.selectNodes("*//eac:identity");
                for (Node idNode : idNodes) {
                    // A Map for each name
                    idMap = new HashMap<>();
                    // Get all the names this ID lists
                    nameList = getNames(idNode);
                    for (Map<String, String> name : nameList) {
                        idMap.putAll(name);
                    }
                    // Indicate the institution for each one
                    idMap.put(INSTITUTION, institutionString);
                    // And add to the list
                    returnList.add(idMap);
                }
            }
        }

        return returnList;
    }

    private List<Map<String, String>> getNames(Node node) {
        List<Map<String, String>> nameList = new ArrayList<>();

        // Any names for this ID
        @SuppressWarnings("unchecked")
        List<Node> names = node.selectNodes("eac:nameEntry");
        for (Node name : names) {
            Map<String, String> nameMap = getNameMap(name);
            nameList.add(nameMap);
        }

        return nameList;
    }

    private Map<String, String> getNameMap(Node name) {
        Map<String, String> nameMap = new HashMap<>();

        String thisDisplay = null;
        String thisFirstName = null;
        String thisSurname = null;
        String title = null;

        // First name
        Node firstNameNode = name
                .selectSingleNode("eac:part[(@localType=\"forename\") " + "or (@localType=\"givenname\")]");
        if (Objects.nonNull(firstNameNode)) {
            thisFirstName = firstNameNode.getText();
        }

        // Surname
        Node surnameNode = name
                .selectSingleNode("eac:part[(@localType=\"surname\") " + "or (@localType=\"familyname\")]");
        if (Objects.nonNull(surnameNode)) {
            thisSurname = surnameNode.getText();
        }

        // Title
        Node titleNode = name.selectSingleNode("eac:part[@localType=\"title\"]");
        if (Objects.nonNull(titleNode)) {
            title = titleNode.getText();
        }

        // Display Name
        if (Objects.nonNull(thisSurname)) {
            thisDisplay = thisSurname;
            nameMap.put("surname", thisSurname);
            if (Objects.nonNull(thisFirstName)) {
                thisDisplay += ", " + thisFirstName;
                nameMap.put("firstName", thisFirstName);
            }
            if (Objects.nonNull(title)) {
                thisDisplay += " (" + title + ")";
            }
            nameMap.put(DISPLAY_NAME, thisDisplay);
        }

        // Last ditch effort... we couldn't find simple name information
        // from recommended values. So just concatenate what we can see.
        if (Objects.isNull(thisDisplay)) {
            // Find every part
            @SuppressWarnings("unchecked")
            List<Node> parts = name.selectNodes("eac:part");
            for (Node part : parts) {
                String value = getValueFromPart((Element) part);

                // And add to the display name
                if (Objects.isNull(thisDisplay)) {
                    thisDisplay = value;
                } else {
                    thisDisplay += ", " + value;
                }
            }
            nameMap.put(DISPLAY_NAME, thisDisplay);
        }

        return nameMap;
    }

    private String getValueFromPart(Element part) {
        // Grab the value and type of this value
        String value = part.getText();
        String type = part.attributeValue("localType");
        // Build a display value for this part
        if (Objects.nonNull(type)) {
            value += " (" + type + ")";
        }
        return value;
    }

    /**
     * <p>
     * Getter for the NLA Identifier in use by this Identity.
     * </p>
     *
     * @return String The ID from the NLA for this Identity
     */
    public String getId() {
        return nlaId;
    }

    /**
     * <p>
     * Getter for our best estimation on a display name for this Identity.
     * </p>
     *
     * @return String The display name for this Identity
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * <p>
     * Getter for the first name for this Identity.
     * </p>
     *
     * @return String The first name for this Identity
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * <p>
     * Getter for the surname for this Identity.
     * </p>
     *
     * @return String The surname for this Identity
     */
    public String getSurame() {
        return surname;
    }

    /**
     * <p>
     * Getter for the institution for this Identity.
     * </p>
     *
     * @return String The institution for this Identity
     */
    public String getInstitution() {
        return institution;
    }

    /**
     * <p>
     * Getter for the List of Identities observed for this person. The return
     * Objects are Maps containing keys very similar to the methods found on the
     * top-level NLAIdentity Object.
     * </p>
     * <ul>
     * <li>'displayName'</li>
     * <li>'firstName'</li>
     * <li>'surname'</li>
     * <li>'institution'</li>
     * </ul>
     *
     * @return List&lt;Map&lt;String, String&gt;&gt; A List Object containing
     *         identities
     */
    public List<Map<String, String>> getKnownIdentities() {
        return knownIds;
    }

    /**
     * <p>
     * Converts a List of DOM4J Nodes into a List of processed NLAIdentity(s).
     * Individual Nodes that fail to process will be skipped.
     * </p>
     *
     * @param nodes
     *            A List of Nodes to process
     * @return List&lt;NLAIdentity&gt; A List of processed Identities
     */
    public static List<NLAIdentity> convertNodesToIdentities(List<Node> nodes) {
        try {
            return convertNodesToIdentities(nodes, false);
        } catch (SRUException ex) {
            return new ArrayList<>();
        }
    }

    /**
     * <p>
     * Converts a List of DOM4J Nodes into a List of processed NLAIdentity(s).
     * Must indicate whether or not errors should cause processing to halt.
     * </p>
     *
     * @param nodes
     *            A List of Nodes to process
     * @param haltOnErrors
     *            Flag if a single Node failing to process should halt
     *            execution.
     * @return List&lt;NLAIdentity&gt; A List of processed Identities
     * @throws SRUException
     *             If 'haltOnErrors' is set to TRUE and a Node fails to process.
     */
    public static List<NLAIdentity> convertNodesToIdentities(List<Node> nodes, boolean haltOnErrors)
            throws SRUException {
        List<NLAIdentity> response = new ArrayList<>();
        // Sanity check
        if (nodes == null || nodes.isEmpty()) {
            return response;
        }
        // Process each Node in turn
        for (Node node : nodes) {
            try {
                NLAIdentity newId = new NLAIdentity(node);
                response.add(newId);
                // Only halt if requested
            } catch (SRUException ex) {
                logger.error("Unable to process identity: ", ex);
                if (haltOnErrors) {
                    throw ex;
                }
            }
        }
        return response;
    }
}

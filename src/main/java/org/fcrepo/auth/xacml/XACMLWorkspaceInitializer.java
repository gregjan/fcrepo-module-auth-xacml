/**
 * Copyright 2014 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.auth.xacml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;

import org.apache.commons.io.FileUtils;
import org.fcrepo.http.commons.session.SessionFactory;
import org.fcrepo.kernel.Datastream;
import org.fcrepo.kernel.exception.InvalidChecksumException;
import org.fcrepo.kernel.services.DatastreamService;
import org.fcrepo.kernel.services.NodeService;
import org.modeshape.jcr.api.nodetype.NodeTypeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Sets up node types and default policies for the XACML Authorization Delegate.
 *
 * @author Gregory Jansen
 */
public class XACMLWorkspaceInitializer {

    /**
     * Class-level logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(XACMLWorkspaceInitializer.class);

    /**
     * Fedora's ModeShape session factory.
     */
    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private DatastreamService datastreamService;

    private File initialPoliciesDirectory;

    private File initialRootPolicyFile;

    /**
     * @return the initialRootPolicyFile
     */
    public File getInitialRootPolicyFile() {
        return initialRootPolicyFile;
    }

    /**
     * @param initialRootPolicyFile the initialRootPolicyFile to set
     */
    public void setInitialRootPolicyFile(final File initialRootPolicyFile) {
        this.initialRootPolicyFile = initialRootPolicyFile;
    }

    /**
     * @return the initialPoliciesDirectory
     */
    public File getInitialPoliciesDirectory() {
        return initialPoliciesDirectory;
    }

    /**
     * @param initialPoliciesDirectory the initialPoliciesDirectory to set
     */
    public void setInitialPoliciesDirectory(final File initialPoliciesDirectory) {
        this.initialPoliciesDirectory = initialPoliciesDirectory;
    }

    /**
     * Initializes node types and default policies.
     */
    public void init() {
        registerNodeTypes();
        loadInitialPolicies();
        linkRootToPolicy();
    }

    /**
     * Set the policy that is effective at the root node.
     */
    private void linkRootToPolicy() {
        Session session = null;
        try {
            session = sessionFactory.getInternalSession();
            session.getRootNode().addMixin("authz:xacmlAssignable");
            final String id = PolicyUtil.getID(FileUtils.openInputStream(initialRootPolicyFile));
            final String repoPath = PolicyUtil.getPathForId(id);
            final Node globalPolicy = session.getNode(repoPath);
            session.getRootNode().setProperty("authz:policy", globalPolicy);
            session.save();
        } catch (final RepositoryException | IOException e) {
            throw new Error("Cannot configure root mix-in or policy", e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    /**
     * Create nodes for the default XACML policy set. Policies are created at paths according to their IDs.
     */
    private void loadInitialPolicies() {
        Session session;
        try {
            session = sessionFactory.getInternalSession();
            // final FedoraResource policies = nodeService.findOrCreateObject(session, "/policies");
            for (final File p : initialPoliciesDirectory.listFiles()) {
                // final String baseName = FilenameUtils.getBaseName(p.getName());
                final String id = PolicyUtil.getID(FileUtils.openInputStream(p));
                final String repoPath = PolicyUtil.getPathForId(id);
                final Datastream d =
                        datastreamService.createDatastream(session, repoPath, "application/xml", p
                        .getAbsolutePath(),
                        new FileInputStream(p));
                LOGGER.info("Add initial policy {} at {}", p.getAbsolutePath(), d.getPath());
            }
            session.save();
        } catch (final RepositoryException | InvalidChecksumException | IOException e) {
            throw new Error("Cannot create default root policies", e);
        }
    }

    private void registerNodeTypes() {
        Session session = null;
        try {
            session = sessionFactory.getInternalSession();
            final NodeTypeManager mgr = (NodeTypeManager) session.getWorkspace().getNodeTypeManager();
            final URL cnd = XACMLWorkspaceInitializer.class.getResource("/cnd/xacml-policy.cnd");
            final NodeTypeIterator nti = mgr.registerNodeTypes(cnd, true);
            while (nti.hasNext()) {
                final NodeType nt = nti.nextNodeType();
                LOGGER.debug("registered node type: {}", nt.getName());
            }
            session.save();
            LOGGER.debug("Registered XACML policy node types");
        } catch (final RepositoryException | IOException e) {
            throw new Error("Cannot register XACML policy node types", e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }
}

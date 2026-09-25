package com.rixon.learn.spring.data.databricks.service;

import com.rixon.learn.spring.data.databricks.model.CatalogInfo;
import com.rixon.learn.spring.data.databricks.model.SchemaInfo;
import com.rixon.learn.spring.data.databricks.model.TableInfo;
import com.rixon.learn.spring.data.databricks.model.WorkspaceUserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for Databricks Control-Plane operations.
 * ALL methods in this service execute against Unity Catalog and Workspace metadata APIs,
 * which consume 0 compute resources and incur $0.00 DBUs.
 */
@Service
public class DatabricksWorkspaceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabricksWorkspaceService.class);

    private final DatabricksRestClient restClient;

    public DatabricksWorkspaceService(DatabricksRestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Retrieves the current authenticated user's profile information.
     */
    public WorkspaceUserInfo getCurrentUser() {
        LOGGER.info("Fetching current Databricks user info from SCIM API...");
        return restClient.getCurrentUser();
    }

    /**
     * Lists all Unity Catalog catalogs available to the workspace.
     * This is a 100% Free Zone operation.
     */
    public List<CatalogInfo> listCatalogs() {
        LOGGER.info("Querying Unity Catalog catalogs (0 compute DBUs)...");
        return restClient.listCatalogs();
    }

    /**
     * Lists schemas in a catalog (e.g. 'samples' or 'system').
     * This is a 100% Free Zone operation.
     */
    public List<SchemaInfo> listSchemas(String catalogName) {
        LOGGER.info("Querying Unity Catalog schemas for catalog '{}' (0 compute DBUs)...", catalogName);
        return restClient.listSchemas(catalogName);
    }

    /**
     * Lists tables and columns in a catalog schema (e.g. 'samples.tpch').
     * This is a 100% Free Zone operation.
     */
    public List<TableInfo> listTables(String catalogName, String schemaName) {
        LOGGER.info("Querying Unity Catalog tables for '{}.{}' (0 compute DBUs)...", catalogName, schemaName);
        return restClient.listTables(catalogName, schemaName);
    }

    /**
     * Checks if the Databricks workspace is reachable and credentials are valid.
     */
    public boolean isWorkspaceReachable() {
        try {
            WorkspaceUserInfo user = getCurrentUser();
            return user != null && user.getUserName() != null;
        } catch (Exception e) {
            LOGGER.debug("Workspace connectivity check failed: {}", e.getMessage());
            return false;
        }
    }
}

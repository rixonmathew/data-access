package com.rixon.learn.spring.data.databricks.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "databricks")
public class DatabricksProperties {

    /**
     * The full Databricks workspace URL as provided by the user.
     */
    private String workspaceUrl = "https://dbc-06bb552d-57db.cloud.databricks.com/?o=2102279257150258";

    /**
     * Explicit host URL if different from workspaceUrl origin.
     */
    private String host;

    /**
     * Databricks organization / workspace ID.
     */
    private String orgId;

    /**
     * Personal Access Token (PAT) for Databricks REST API / JDBC.
     */
    private String token;

    /**
     * Optional default cluster ID for workloads.
     */
    private String clusterId;

    /**
     * Optional default SQL Warehouse / Endpoint ID.
     */
    private String warehouseId;

    /**
     * HTTP Path for Databricks JDBC driver (e.g. /sql/1.0/warehouses/... or /sql/protocolv1/o/2102279257150258/...).
     */
    private String httpPath;

    /**
     * Strict Free-Tier Guardrail flag. When true, forbids any configuration or execution
     * that risks incurring unwanted cloud compute or DBU charges.
     */
    private boolean freeTierOnly = true;

    /**
     * Maximum allowed idle minutes before an active cluster MUST automatically terminate.
     * Databricks defaults to 120 mins if unspecified; our guard enforces max 10-15 mins.
     */
    private int maxAutoTerminationMinutes = 10;

    /**
     * Maximum allowed SQL statement runtime in seconds before timing out to prevent runaway compute.
     */
    private int maxStatementTimeoutSeconds = 30;

    /**
     * Maximum result rows to fetch in a single guarded query.
     */
    private int maxQueryRows = 100;

    /**
     * Enforce single-node cluster architecture (0 worker nodes, driver acts as local master).
     */
    private boolean enforceSingleNode = true;

    /**
     * Automatically shut down any running clusters when application context closes.
     */
    private boolean autoShutdownOnExit = true;

    /**
     * Set of allowed instance types approved for free-tier / minimal cost testing.
     */
    private Set<String> allowedNodeTypes = new HashSet<>(Arrays.asList(
            "t3.medium",
            "t3.large",
            "t3.xlarge",
            "m5.large",
            "i3.xlarge",
            "SingleNode",
            "local[*]"
    ));

    public String resolveHost() {
        String dotenvHost = DotenvLoader.get("DATABRICKS_HOST");
        if (dotenvHost != null && !dotenvHost.isBlank()) {
            return dotenvHost.replaceAll("/+$", "");
        }
        if (host != null && !host.isBlank()) {
            return host.replaceAll("/+$", "");
        }
        String dotenvUrl = DotenvLoader.get("DATABRICKS_WORKSPACE_URL");
        String effectiveUrl = (dotenvUrl != null && !dotenvUrl.isBlank()) ? dotenvUrl : workspaceUrl;
        if (effectiveUrl != null && !effectiveUrl.isBlank()) {
            try {
                URI uri = URI.create(effectiveUrl);
                return uri.getScheme() + "://" + uri.getHost();
            } catch (Exception ignored) {
            }
        }
        return "https://dbc-06bb552d-57db.cloud.databricks.com";
    }

    public String resolveOrgId() {
        String dotenvOrg = DotenvLoader.get("DATABRICKS_ORG_ID");
        if (dotenvOrg != null && !dotenvOrg.isBlank()) {
            return dotenvOrg;
        }
        if (orgId != null && !orgId.isBlank()) {
            return orgId;
        }
        String dotenvUrl = DotenvLoader.get("DATABRICKS_WORKSPACE_URL");
        String effectiveUrl = (dotenvUrl != null && !dotenvUrl.isBlank()) ? dotenvUrl : workspaceUrl;
        if (effectiveUrl != null && effectiveUrl.contains("o=")) {
            try {
                int idx = effectiveUrl.indexOf("o=");
                String sub = effectiveUrl.substring(idx + 2);
                int amp = sub.indexOf('&');
                return amp != -1 ? sub.substring(0, amp) : sub;
            } catch (Exception ignored) {
            }
        }
        return "2102279257150258";
    }

    public String getToken() {
        if (token != null && !token.isBlank() && !token.equalsIgnoreCase("REPLACE_WITH_YOUR_PAT")) {
            return token;
        }
        String dotenvToken = DotenvLoader.get("DATABRICKS_TOKEN");
        if (dotenvToken != null && !dotenvToken.isBlank()) {
            return dotenvToken;
        }
        return token;
    }

    public boolean hasToken() {
        String resolved = getToken();
        return resolved != null && !resolved.isBlank() && !resolved.equalsIgnoreCase("REPLACE_WITH_YOUR_PAT");
    }
}

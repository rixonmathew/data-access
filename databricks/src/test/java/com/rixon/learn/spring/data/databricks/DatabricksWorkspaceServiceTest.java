package com.rixon.learn.spring.data.databricks;

import com.rixon.learn.spring.data.databricks.model.CatalogInfo;
import com.rixon.learn.spring.data.databricks.model.SchemaInfo;
import com.rixon.learn.spring.data.databricks.model.TableInfo;
import com.rixon.learn.spring.data.databricks.model.WorkspaceUserInfo;
import com.rixon.learn.spring.data.databricks.service.DatabricksRestClient;
import com.rixon.learn.spring.data.databricks.service.DatabricksWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabricksWorkspaceServiceTest {

    @Mock
    private DatabricksRestClient restClient;

    private DatabricksWorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        workspaceService = new DatabricksWorkspaceService(restClient);
    }

    @Test
    @DisplayName("getCurrentUser delegates to rest client")
    void testGetCurrentUser() {
        WorkspaceUserInfo user = WorkspaceUserInfo.builder()
                .id("2102279257150258")
                .userName("user@example.com")
                .displayName("Test User")
                .active(true)
                .build();
        when(restClient.getCurrentUser()).thenReturn(user);

        WorkspaceUserInfo result = workspaceService.getCurrentUser();

        assertThat(result).isSameAs(user);
        verify(restClient).getCurrentUser();
    }

    @Test
    @DisplayName("listCatalogs fetches Unity Catalog catalogs at zero compute cost")
    void testListCatalogs() {
        List<CatalogInfo> catalogs = List.of(
                CatalogInfo.builder().name("samples").catalogType("SYSTEM_CATALOG").build()
        );
        when(restClient.listCatalogs()).thenReturn(catalogs);

        List<CatalogInfo> result = workspaceService.listCatalogs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("samples");
        verify(restClient).listCatalogs();
    }

    @Test
    @DisplayName("listSchemas fetches schemas for a catalog")
    void testListSchemas() {
        List<SchemaInfo> schemas = List.of(
                SchemaInfo.builder().name("tpch").catalogName("samples").build()
        );
        when(restClient.listSchemas("samples")).thenReturn(schemas);

        List<SchemaInfo> result = workspaceService.listSchemas("samples");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("tpch");
        verify(restClient).listSchemas("samples");
    }

    @Test
    @DisplayName("listTables fetches tables for a schema")
    void testListTables() {
        List<TableInfo> tables = List.of(
                TableInfo.builder().name("customer").catalogName("samples").schemaName("tpch").build()
        );
        when(restClient.listTables("samples", "tpch")).thenReturn(tables);

        List<TableInfo> result = workspaceService.listTables("samples", "tpch");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("customer");
        verify(restClient).listTables("samples", "tpch");
    }

    @Test
    @DisplayName("isWorkspaceReachable returns true when user info is present")
    void testIsWorkspaceReachable() {
        when(restClient.getCurrentUser()).thenReturn(WorkspaceUserInfo.builder().userName("test").build());
        assertThat(workspaceService.isWorkspaceReachable()).isTrue();
    }

    @Test
    @DisplayName("isWorkspaceReachable returns false on network or auth failure")
    void testIsWorkspaceReachableOnFailure() {
        when(restClient.getCurrentUser()).thenThrow(new RuntimeException("Connection refused"));
        assertThat(workspaceService.isWorkspaceReachable()).isFalse();
    }
}

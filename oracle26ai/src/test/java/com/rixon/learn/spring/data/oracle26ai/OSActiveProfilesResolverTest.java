package com.rixon.learn.spring.data.oracle26ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OSActiveProfilesResolverTest {

    private String originalOsName;
    private String originalOsVersion;

    @BeforeEach
    void setUp() {
        originalOsName = System.getProperty("os.name");
        originalOsVersion = System.getProperty("os.version");
    }

    @AfterEach
    void tearDown() {
        System.setProperty("os.name", originalOsName);
        System.setProperty("os.version", originalOsVersion);
    }

    @Test
    void testResolveWindows() {
        System.setProperty("os.name", "Windows 11");
        OSActiveProfilesResolver resolver = new OSActiveProfilesResolver();
        String[] profiles = resolver.resolve(null);
        assertThat(profiles).containsExactly("oracle-cloud-win");
    }

    @Test
    void testResolveLinux() {
        System.setProperty("os.name", "Linux");
        System.setProperty("os.version", "5.4.0-100-generic");
        OSActiveProfilesResolver resolver = new OSActiveProfilesResolver();
        String[] profiles = resolver.resolve(null);
        assertThat(profiles).containsExactly("oracle-cloud");
    }

    @Test
    void testResolveWSL() {
        System.setProperty("os.name", "Linux");
        System.setProperty("os.version", "5.10.16.3-microsoft-standard-WSL2");
        OSActiveProfilesResolver resolver = new OSActiveProfilesResolver();
        String[] profiles = resolver.resolve(null);
        // This will fail before the fix
        assertThat(profiles).containsExactly("oracle-cloud-win");
    }
}

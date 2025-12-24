package com.rixon.learn.spring.data.oracle26ai;

import org.springframework.test.context.ActiveProfilesResolver;

public class OSActiveProfilesResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new String[]{"oracle-cloud-win"};
        } else {
            // Assuming non-windows is linux/mac and should use oracle-cloud
            return new String[]{"oracle-cloud"};
        }
    }
}

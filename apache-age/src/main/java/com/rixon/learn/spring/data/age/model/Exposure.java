package com.rixon.learn.spring.data.age.model;

/** A {@code (from)-[:HAS_EXPOSURE_TO]->(to)} edge. */
public record Exposure(String fromAccount, String toAccount, String relationshipType,
                       double exposureLimit, double currentExposure) {
}

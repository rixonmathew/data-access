package com.rixon.learn.spring.data.age.model;

/** Aggregate over all exposure edges of one relationship type. */
public record ExposureSummary(String relationshipType, long edges, double totalExposure) {
}

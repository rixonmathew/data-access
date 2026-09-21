package com.rixon.learn.spring.data.models;

import lombok.*;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@Getter
@Setter
@ToString(exclude = "targetAccount")
@EqualsAndHashCode(of = {"relationshipType", "exposureLimit", "currentExposure"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RelationshipProperties
public class CounterpartyExposure {

    @RelationshipId
    @GeneratedValue
    private Long id;

    @TargetNode
    private AccountNode targetAccount;

    private String relationshipType;

    private double exposureLimit;

    private double currentExposure;
}

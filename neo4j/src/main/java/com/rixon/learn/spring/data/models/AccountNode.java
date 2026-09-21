package com.rixon.learn.spring.data.models;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@ToString(exclude = "exposures")
@EqualsAndHashCode(of = "accountNumber")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Node("Account")
public class AccountNode {

    @Id
    private String accountNumber;

    private String holderName;

    private String accountType;

    @Relationship(type = "HAS_EXPOSURE_TO", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private Set<CounterpartyExposure> exposures = new HashSet<>();

    public void addExposure(AccountNode target, String relationshipType, double exposureLimit, double currentExposure) {
        if (exposures == null) {
            exposures = new HashSet<>();
        }
        exposures.add(CounterpartyExposure.builder()
                .targetAccount(target)
                .relationshipType(relationshipType)
                .exposureLimit(exposureLimit)
                .currentExposure(currentExposure)
                .build());
    }
}

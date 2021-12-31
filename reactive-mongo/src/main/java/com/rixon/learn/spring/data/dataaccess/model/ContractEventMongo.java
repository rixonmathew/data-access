package com.rixon.learn.spring.data.dataaccess.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "contract_events")
@Data
public class ContractEventMongo {
    @Id
    private String id;
    private String contractId;
    private String type;
    private LocalDate date;
    private String description;
}

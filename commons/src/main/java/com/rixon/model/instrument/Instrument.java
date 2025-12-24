package com.rixon.model.instrument;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Entity
@Table(name = "INSTRUMENT")
public class Instrument {
    @Id
    @GeneratedValue
    @org.springframework.data.annotation.Id
    private long id;
    private String type;
    private String name;
    private String metadata;
}

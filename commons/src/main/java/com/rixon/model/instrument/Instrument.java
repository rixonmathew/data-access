package com.rixon.model.instrument;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Instrument {
    @Id
    @GeneratedValue
    private long id;
    private String type;
    private String name;
    private String metadata;
}

package com.rixon.model.instrument;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Entity
@Table(name = "INSTRUMENT")
public class Instrument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @org.springframework.data.annotation.Id
    @Column("ID")
    private Long id;

    @Column("TYPE")
    private String type;

    @Column("NAME")
    private String name;

    @Column("METADATA")
    private String metadata;
}

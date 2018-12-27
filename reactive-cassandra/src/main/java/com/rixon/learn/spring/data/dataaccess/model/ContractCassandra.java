package com.rixon.learn.spring.data.dataaccess.model;

import lombok.Data;
import lombok.ToString;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Data
@ToString
@Table("contract")
public class ContractCassandra {

    @PrimaryKey
    private Integer id;
    @Column("account_id")
    private String accountId;
    @Column("asset_id")
    private String assetId;


}

package com.rixon.learn.spring.data.elasticsearch.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "instruments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstrumentDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String ticker;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String assetClass;

    @Field(type = FieldType.Keyword)
    private String sector;

    @Field(type = FieldType.Double)
    private Double marketCapBillions;

    @Field(type = FieldType.Keyword)
    private String exchange;

    @Field(type = FieldType.Double)
    private Double lastTradedPrice;
}

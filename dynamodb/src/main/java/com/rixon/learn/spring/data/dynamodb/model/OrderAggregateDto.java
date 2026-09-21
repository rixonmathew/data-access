package com.rixon.learn.spring.data.dynamodb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAggregateDto {
    private SingleTableOrderEntity order;
    @Builder.Default
    private List<SingleTableExecutionEntity> executions = new ArrayList<>();
}

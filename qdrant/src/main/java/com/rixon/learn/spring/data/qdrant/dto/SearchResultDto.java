package com.rixon.learn.spring.data.qdrant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResultDto {
    private long id;
    private float score;
    private String ticker;
    private String title;
    private String sector;
    private String sentiment;
}

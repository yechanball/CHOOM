package com.choom.domain.dance.dto;

import com.google.api.services.youtube.model.SearchListResponse;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class DanceSearchDto {
    String nextPageToken;
    String prevPageToken;
    Integer totalResults;
    Integer resultsPerPage;
    List<DanceDetailsDto> search;

    @Builder
    public DanceSearchDto(SearchListResponse searchListResponse, List<DanceDetailsDto> danceDetailDtoList) {
        this.nextPageToken = searchListResponse.getNextPageToken();
        this.prevPageToken = searchListResponse.getPrevPageToken();
        this.totalResults = searchListResponse.getPageInfo().getTotalResults();
        this.resultsPerPage = searchListResponse.getPageInfo().getResultsPerPage();
        this.search = danceDetailDtoList;
    }
}

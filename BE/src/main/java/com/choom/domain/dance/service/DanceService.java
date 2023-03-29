package com.choom.domain.dance.service;

import com.choom.domain.bookmark.entity.BookmarkRepository;
import com.choom.domain.dance.dto.DanceDetailsWithRankDto;
import com.choom.domain.dance.dto.DanceDetailsDto;
import com.choom.domain.dance.dto.DanceSearchDto;
import com.choom.domain.dance.dto.PopularDanceDto;
import com.choom.domain.dance.dto.DanceRankUserDto;
import com.choom.domain.dance.dto.DanceStatusDto;
import com.choom.domain.dance.entity.Dance;
import com.choom.domain.dance.entity.DanceRepository;
import com.choom.domain.mydance.entity.MyDance;
import com.choom.domain.mydance.entity.MyDanceRepository;
import com.choom.global.service.FileService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import com.sapher.youtubedl.YoutubeDL;
import com.sapher.youtubedl.YoutubeDLException;
import com.sapher.youtubedl.YoutubeDLRequest;
import com.sapher.youtubedl.YoutubeDLResponse;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.api.services.youtube.YouTube;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class DanceService {

    private final DanceRepository danceRepository;
    private final MyDanceRepository myDanceRepository;
    private final BookmarkRepository  bookmarkRepository;
    private final FileService fileService;

    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();
    private static YouTube youtube;

    private static final String GOOGLE_YOUTUBE_URL =  "https://www.youtube.com/shorts/";
    private static final String YOUTUBE_SEARCH_FIELDS1 = "nextPageToken, prevPageToken, pageInfo, items(id/videoId,snippet/title,snippet/channelTitle)";
    private static final String YOUTUBE_SEARCH_FIELDS2 = "items(contentDetails/duration,snippet/title, snippet/description,snippet/publishedAt, snippet/thumbnails/high/url,statistics/likeCount,statistics/viewCount)";

    private static String YOUTUBE_APIKEY;
    @Value("${apikey.youtube}")
    public void setKey(String value){
        YOUTUBE_APIKEY = value;
    }

    static {
        youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpRequestInitializer() {
            public void initialize(HttpRequest request) throws IOException {
            }
        }).setApplicationName("youtube-cmdline-search-sample").build();
    }

    public DanceSearchDto searchDance(String keyword, String pageToken,Long size) {
        log.info("Starting YouTube search... " +keyword+" pageToken : "+pageToken);
        List<DanceDetailsDto> danceDetailDtoList = new ArrayList<>();

        SearchListResponse searchResponse = null;

        try {
            // 1. 유튜브 검색 결과
            if (youtube != null) {
                YouTube.Search.List search = youtube.search().list("snippet");
                search.setKey(YOUTUBE_APIKEY);
                search.setQ(keyword);
                search.setType("video");
                search.setVideoDuration("short");
                search.setMaxResults(size);
                search.setFields(YOUTUBE_SEARCH_FIELDS1);
                search.setPageToken(pageToken);

                searchResponse = search.execute();

                List<SearchResult> searchResultList = searchResponse.getItems();

                if (searchResultList != null) {
                    for (SearchResult video : searchResultList) {
                        // 비동기로 검색 -> 검색 속도 향상
                        String youtubeId = video.getId().getVideoId();
                        DanceDetailsDto danceDetailDto = getVideoDetail(youtubeId);

                        if (danceDetailDto != null)
                            danceDetailDtoList.add(danceDetailDto);
                    }
                }
            }

        } catch (GoogleJsonResponseException e){
            log.info("There was a service error: " + e.getDetails().getCode() + " : "  + e.getDetails().getMessage());
        } catch(IOException e){
            log.info("There was an IO error: " + e.getCause() + " : " + e.getMessage());
        } catch(Throwable t){
            t.printStackTrace();
        }

        Collections.sort(danceDetailDtoList, (o1, o2) -> { //new Comparator<YoutubeResponseDto>() -> lambda
            // 챌린지 참여자 수 -> 원본 영상 시청자 순 으로 정렬
            if(o1.getUserCount() == o2.getUserCount()){
                return (int)(o2.getViewCount()- o1.getViewCount());
            }else{
                return o2.getUserCount() - o1.getUserCount();
            }
        });
        DanceSearchDto danceSearchDto = DanceSearchDto.builder()
            .searchListResponse(searchResponse)
            .danceDetailDtoList(danceDetailDtoList)
            .build();
        return danceSearchDto;
    }

    @Async
    DanceDetailsDto getVideoDetail(String youtubeId)throws IOException {
        YouTube.Videos.List videoDetails =  youtube.videos().list("contentDetails");
        videoDetails.setKey(YOUTUBE_APIKEY);
        videoDetails.setId(youtubeId);
        videoDetails.setPart("statistics,snippet,contentDetails");
        videoDetails.setFields(YOUTUBE_SEARCH_FIELDS2);

        if(videoDetails.execute().getItems().size() == 0){
            throw new IllegalArgumentException("유튜브 동영상 id가 올바르지 않습니다...");
        }
        Video videoDetail = videoDetails.execute().getItems().get(0);

        //1분 이내 영상인지 확인
        String time = videoDetail.getContentDetails().getDuration();
        if(time.equals("P0D") || time.contains("M")){ // P0D는 라이브 방송
            return null;
        }

        Long likeCount = 0L;
        if(videoDetail.getStatistics().getLikeCount()!=null){
            likeCount = videoDetail.getStatistics().getLikeCount().longValue();
        }
        Long viewCount = 0L;
        if(videoDetail.getStatistics().getViewCount()!=null){
            viewCount = videoDetail.getStatistics().getViewCount().longValue();
        }

        String url = GOOGLE_YOUTUBE_URL + youtubeId;
        Dance dance = danceRepository.findByUrl(url).orElse(null);

        Long id = null;
        if(dance != null){
            id = dance.getId();
        }

        int userCount = 0;
        int status = 0;
        if(dance != null){
            userCount = dance.getUserCount();
            status = dance.getStatus();
        }
        String publishedAt = String.valueOf(videoDetail.getSnippet().getPublishedAt()).split("T")[0];
        //1분 이내인 경우
        int s = Integer.parseInt(time.split("T")[1].split("S")[0]);
        DanceDetailsDto danceDetailDto = DanceDetailsDto.builder()
            .id(id)
            .url(url)
            .videoDetail(videoDetail)
            .thumbnailPath(videoDetail.getSnippet().getThumbnails().getHigh().getUrl())
            .sec(s)
            .likeCount(likeCount)
            .viewCount(viewCount)
            .userCount(userCount)
            .youtubeId(youtubeId)
            .status(status)
            .publishedAt(publishedAt)
            .build();
        return danceDetailDto;
    }

    @Transactional
    public Long addDance(String youtubeId) throws IOException {
        String url = GOOGLE_YOUTUBE_URL+youtubeId;

        Dance dance = danceRepository.findByUrl(url).orElse(null);

        if(dance == null) { //처음인경우
            DanceDetailsDto danceDetailDto = getVideoDetail(youtubeId);

            Dance insertDance = Dance.builder()
                .danceDetailDto(danceDetailDto)
                .build();

            dance = danceRepository.save(insertDance);
        }
        return dance.getId();
    }

    @Transactional
    public void saveResult(Long danceId, MultipartFile jsonFile) throws IOException {
        // JSON파일 서버에 저장
        String jsonPath = fileService.fileUpload("coordinate", jsonFile);
        log.info("변경 된 jsonPath : "+jsonPath);
        // DB에 파일 위치 UPDATE
        Dance dance = danceRepository.findById(danceId)
            .orElseThrow(()->new IllegalArgumentException("존재하지 않는 Dance id값 입니다."));
        dance.updateJsonPath(jsonPath);
        dance.changeStatus(2); //분석 완료 상태로 변경
    }

    @Transactional
    public DanceDetailsWithRankDto findDance(Long userId, Long danceId) throws IOException {
        Dance dance = danceRepository.findById(danceId)
            .orElseThrow(() -> new IllegalArgumentException("DB에 없는 Dance Id 입니다!!"));

        String youtubeId = dance.getYoutubeId();
        String url = GOOGLE_YOUTUBE_URL+youtubeId;

        // 1. 검색하기 (유튜브API 통해 자세한 동영상 정보 가져오기)
        DanceDetailsDto danceDetailDto = getVideoDetail(youtubeId);
        log.info("1차 검색 정보 : " + danceDetailDto);

        // 2. 저장하기 (처음 참여한 경우에만)
        dance = danceRepository.findByUrl(url).orElse(null);

        List<DanceRankUserDto> danceRankUserDtoList = new ArrayList<>();

        if(dance == null){ //처음인경우
            //3. DB에 저장
            Dance insertDance = Dance.builder()
                .danceDetailDto(danceDetailDto)
                .build();
            Dance savedDance = danceRepository.save(insertDance);
            danceDetailDto.setId(savedDance.getId());

        }else{ //처음이 아닌 경우
            // 3. 상위 순위 유저 3명 (처음인 경우에는 순위가 0임)
            List<MyDance> myDanceList = myDanceRepository.findRankingUser(dance);
            danceRankUserDtoList = myDanceList.stream().map(myDance ->
                DanceRankUserDto.builder()
                    .myDance(myDance)
                    .build()
            ).collect(
                Collectors.toList());

            // 찜한 첼린지 인지 체크하기
            if(bookmarkRepository.findBookmarkByUserIdAndDanceId(userId, dance.getId()).isPresent()){
                danceDetailDto.setBookmark(true);
            }
        }

        DanceDetailsWithRankDto danceDetailWithRankDto = DanceDetailsWithRankDto.builder()
            .danceDetailDto(danceDetailDto)
            .danceRankUserDtoList(danceRankUserDtoList)
            .build();

        return danceDetailWithRankDto;
    }

    public List<PopularDanceDto> findPopularDance() {
        List<Dance> danceList = danceRepository.findPopularDance();
        List<PopularDanceDto> popularDanceDtoList = danceList.stream()
            .map(PopularDanceDto::new)
            .collect( Collectors.toList());
        return popularDanceDtoList;
    }

    @Transactional
    public DanceStatusDto checkDanceStatus(Long danceId)
        throws YoutubeDLException, UnknownHostException {
        Dance dance = danceRepository.findById(danceId)
            .orElseThrow(()->new IllegalArgumentException("존재하지 않는 Dance id값 입니다."));
        int status  = dance.getStatus();
        DanceStatusDto danceStatusDto = null;
        if(status == 0){ // 분석 안된 상태
            log.info("아직 분석 안 된 영상!!");
            dance.changeStatus(1);
            // 동영상 다운로드
            String url = dance.getUrl();

            String videopath = youtubeDownload(url);
            dance.saveVideoPath(videopath);

            danceStatusDto = DanceStatusDto.builder()
                .status(0)
                .videoPath(videopath)
                .build();

        }else if(status == 1){ // 분석 중인 상태
            log.info("분석 중 인 영상!!"); // 분석 완료 될때까지 기다려야됨???
            danceStatusDto = DanceStatusDto.builder()
                .videoPath(dance.getVideoPath())
                .status(0) //나중에 1로 변경해야됨!!!!!!!!!!!!!!
                .build();
        }else{ // 분석 완료인 상태
            log.info("이미 분석 완료 된 영상!!");
            danceStatusDto = DanceStatusDto.builder()
                .status(2)
                .videoPath(dance.getVideoPath())
                .jsonPath(dance.getJsonPath())
                .build();
        }
        return danceStatusDto;
    }

    public String youtubeDownload(String url) throws YoutubeDLException, UnknownHostException {
        String hostname = InetAddress.getLocalHost().getHostName();
        String path = "";
        File file = null;

        String youtubeId = url.split("/")[4];

        if (hostname.substring(0, 7).equals("DESKTOP")) {
            path = "C:/choom/youtube/";
        } else {
            path = "/var/lib/choom/youtube/";
        }
        file = new File(path + youtubeId);

        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();

        // Build request
        YoutubeDLRequest request = new YoutubeDLRequest(url, path);
        request.setOption("ignore-errors");		// --ignore-errors
        request.setOption("output", "%(id)s.mp4");	// --output "%(id)s"
        request.setOption("retries", 10);		// --retries 10

        YoutubeDL.setExecutablePath(path+"youtube-dl");

        // Make request and return response
        YoutubeDLResponse response = YoutubeDL.execute(request);

        return "/choom/youtube/" + youtubeId + ".mp4";
    }
}
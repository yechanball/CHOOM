package com.choom.domain.user.service;

import com.choom.domain.mydance.entity.MyDance;
import com.choom.domain.user.dto.SocialUserInfoDto;
import com.choom.domain.user.dto.TokenDto;
import com.choom.domain.user.entity.*;
import com.choom.global.service.FileService;
import com.choom.global.service.GoogleService;
import com.choom.global.service.KakaoService;
import com.choom.global.service.RandomNicknameService;
import com.choom.global.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final FileService fileService;
    private final RedisService redisService;
    private final KakaoService kakaoService;
    private final GoogleService googleService;
    private final UserRepository userRepository;
    private final RandomNicknameService randomNicknameService;
    private final BlacklistRedisRepository blacklistRedisRepository;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;

    @Transactional
    public TokenDto socialLogin(String type, String code) {
        SocialUserInfoDto userInfo = null;
        SocialType socialType = null;

        if ("GOOGLE".equals(type)) {
            userInfo = googleService.getUserInfo(code);
            socialType = SocialType.GOOGLE;
        } else if ("KAKAO".equals(type)) {
            userInfo = kakaoService.getUserInfo(code);
            socialType = SocialType.KAKAO;
        }

        String identifier = userInfo.getIdentifier();
        String profileImage = userInfo.getProfileImage();

        User user = userRepository.findByIdentifierAndSocialType(identifier, socialType).orElse(null);

        if (user == null) {
            log.info("newuser");
            User newUser = addUser(identifier, profileImage, socialType);
            return issueToken(newUser);
        }

        return issueToken(user);
    }

    @Transactional
    public User addUser(String identifier, String profileImage, SocialType socialType) {
        String nickname = randomNicknameService.getRandomNickname("text", 1, 10, "_");
        String profileImagePath = fileService.saveProfileImage("user", nickname, profileImage);
        User user = User.builder()
                .identifier(identifier)
                .nickname(nickname)
                .profileImage(profileImagePath)
                .socialType(socialType)
                .build();
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        fileService.fileDelete(user.getProfileImage());
        List<MyDance> myDanceList = user.getMyDanceList();
        for (MyDance myDance : myDanceList) {
            String videoPath = myDance.getVideoPath();
            String thumbnailPath = myDance.getThumbnailPath();
            fileService.fileDelete(videoPath);
            fileService.fileDelete(thumbnailPath);
        }
        userRepository.delete(user);
    }

    public TokenDto issueToken(User user) {
        TokenDto token = JwtTokenUtil.getToken(user.getIdentifier());
        redisService.saveToken(user.getId(), token.getRefreshToken());
        Optional<RefreshToken> token1 = refreshTokenRedisRepository.findByToken(token.getRefreshToken());
        if (token1.isEmpty()) {
            log.info("refreshToken 저장 안 됨");
        } else {
            log.info("refreshToken 저장 : " + token1.get().getToken());
        }
        return token;
    }

    public TokenDto reissueToken(String refreshToken) {
        RefreshToken oldToken = refreshTokenRedisRepository.findByToken(refreshToken).orElse(null);
        if (oldToken != null) {
            User user = userRepository.findById(oldToken.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
            return issueToken(user);
        }
        return null;
    }

    public String logout(Long userId, String accessToken) {
        blacklistRedisRepository.save(Blacklist.builder()
                .token(accessToken)
                .build());
        RefreshToken token = refreshTokenRedisRepository.findById(userId).orElse(null);
        if (token != null) {
            return redisService.deleteToken(token);
        }
        return null;
    }

    public boolean isBlacklisted(String token) {
        if (blacklistRedisRepository.findById(token).isPresent()) {
            log.info("BlacklistedAccessToken : " + token);
            return true;
        }
        return false;
    }

    public ResponseCookie setCookie(String refreshToken, Integer expiration) {
        log.info("create Cookie");
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("None")
                .maxAge(expiration / 1000)
                .build();
        return cookie;
    }
}
package com.choom.domain.user.service;

import com.choom.domain.user.dto.KakaoOAuth2Dto;
import com.choom.domain.user.dto.KakaoUserInfoDto;
import com.choom.domain.user.entity.SocialType;
import com.choom.domain.user.entity.User;
import com.choom.domain.user.entity.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final KakaoOAuth2Dto kakaoOAuth2Dto;
    private final UserRepository userRepository;

    public Optional<User> findUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findUserByIdentifier(String identifier) {

        return userRepository.findByIdentifier(identifier);
    }

    public void kakaoLogin(String code) {
        log.info(code);
        KakaoUserInfoDto userInfo = kakaoOAuth2Dto.getUserInfo(code);
        String identifier = userInfo.getIdentifier();
        String nickname = userInfo.getNickname();
        String profileImage = userInfo.getProfileImage();

        User user = userRepository.findByIdentifier(identifier).orElse(null);

        if (user == null) {
            user = User.builder()
                    .identifier(identifier)
                    .nickname(nickname)
                    .profileImage(profileImage)
                    .socialType(SocialType.valueOf("KAKAO"))
                    .build();
            userRepository.save(user);
        }
    }
}
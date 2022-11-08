package com.feelmycode.parabole.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feelmycode.parabole.domain.KakaoOauthToken;
import com.feelmycode.parabole.domain.KakaoProfile;
import com.feelmycode.parabole.domain.Seller;
import com.feelmycode.parabole.domain.User;
import com.feelmycode.parabole.dto.UserDto;
import com.feelmycode.parabole.dto.UserInfoResponseDto;
import com.feelmycode.parabole.dto.UserLoginResponseDto;
import com.feelmycode.parabole.dto.UserSearchDto;
import com.feelmycode.parabole.global.error.exception.NoSuchAccountException;
import com.feelmycode.parabole.global.error.exception.ParaboleException;
import com.feelmycode.parabole.global.util.JwtUtils;
import com.feelmycode.parabole.global.util.StringUtil;
import com.feelmycode.parabole.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final CartService cartService;
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${sns.kakao.client-id}")
    private String kakaoClientId;
//    @Value("${sns.kakao.client-secret}")
//    private String kakaoClientSecret;
    @Value("${sns.kakao.redirect-uri}")
    private String kakaoRedirectUri;

    @Transactional
    public UserDto create(UserDto userDTO) {
        String email = userDTO.getEmail();
        if(StringUtil.controllerParamIsBlank(email)) {
            throw new ParaboleException(HttpStatus.BAD_REQUEST, "이메일을 입력하세요.");
        }
        if(userRepository.existsByEmail(email)) {
            log.warn("Email already exists {}", email);
            throw new ParaboleException(HttpStatus.BAD_REQUEST, "이미 사용중인 이메일입니다.");
        }

        User user = User.builder()
            .email(userDTO.getEmail())
            .username(userDTO.getName())
            .nickname(userDTO.getNickname())
            .phone(userDTO.getPhone())
            .password(passwordEncoder.encode(userDTO.getPassword()))
            .imageUrl("https://ssl.pstatic.net/static/cafe/cafe_pc/default/cafe_profile_77.png")
            .role("ROLE_USER")
            .authProvider("Home")
            .build();
        User newUser = userRepository.save(user);

        return UserDto.builder()
            .id(newUser.getId())
            .name(newUser.getUsername())
            .nickname(newUser.getNickname())
            .build();       // welcome page 위한 부분
    }

    public UserLoginResponseDto getByCredentials(UserDto userDto) {
        User originalUser = userRepository.findByEmail(userDto.getEmail());

        if(originalUser != null && passwordEncoder.matches(userDto.getPassword(), originalUser.getPassword())) {
            String token = jwtUtils.generateToken(originalUser);
            log.info("generated Token {}", token);

            return new UserLoginResponseDto(originalUser, token);
        } else {
            throw new NoSuchAccountException();
        }
    }

    public boolean isSeller(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NoSuchAccountException())
            .getRole().equals("ROLE_SELLER");
    }

    public Seller getSeller(Long userId) {
        return getUser(userId).getSeller();
    }
    public UserInfoResponseDto getUserInfo(Long userId) {

        User user = getUser(userId);
        if(user.getRole().equals("ROLE_USER")){
            return new UserInfoResponseDto(user.getEmail(), user.getUsername(), user.getNickname(), "USER", user.getPhone());
        }
        return new UserInfoResponseDto(user.getEmail(), user.getUsername(), user.getNickname(), "SELLER", user.getPhone());
    }
    public User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(
            () -> new ParaboleException(HttpStatus.NOT_FOUND, "해당 사용자Id로 조회되는 사용자가 존재하지 않습니다."));
    }

    public List<UserSearchDto> getNonSellerUsers(String userName) {

        List<User> list;
        if (userName.equals("")) {
            list = userRepository.findAll();
        } else {
            list = userRepository.findAllByUsernameContainsIgnoreCase(userName);
        }
        List<UserSearchDto> dtos = new ArrayList<>();
        for (User u : list) {
            if (u.sellerIsNull()) {
                dtos.add(new UserSearchDto(u.getId(), u.getUsername(), u.getEmail(),
                    u.getPhone()));
            }
        }
        if (dtos.isEmpty()) {
            throw new ParaboleException(HttpStatus.NOT_FOUND, "사용자가 존재하지 않습니다.");
        }
        return dtos;
    }

    public KakaoOauthToken getAccessTokenKakao(String code) {     // (3) fe->be인가 코드 전달, (4) be->카카오 인가코드로 엑세스 토큰 요청

        RestTemplate restTemplate = new RestTemplate();
//        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("client_id", kakaoClientId);
        params.add("redirect_uri", kakaoRedirectUri);
//        params.add("client_secret", kakaoClientSecret);

        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest =
            new HttpEntity<>(params, headers);

        ResponseEntity<String> accessTokenResponse = restTemplate.exchange(
            "https://kauth.kakao.com/oauth/token",
            HttpMethod.POST,
            kakaoTokenRequest,
            String.class
        );

        ObjectMapper objectMapper = new ObjectMapper();
        KakaoOauthToken kakaoOauthToken = null;
        try {
            kakaoOauthToken = objectMapper.readValue(accessTokenResponse.getBody(), KakaoOauthToken.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return kakaoOauthToken;                 // (5) 카카오 -> be 로 발급해준 accessToken
    }

    @Transactional
    public UserLoginResponseDto saveUserAndGetTokenKakao(String token) { // 발급 받은 accessToken 으로 카카오 회원 정보 DB 저장 후 JWT 를 생성
        KakaoProfile profile = findProfileKakao(token);
        log.info(">>>>>>>>>>>> KakaoProfile sent from Kakao (Before Custom) {}", profile.toString());

        User user = userRepository.findByEmail(profile.getKakao_account().getEmail());
        if(user == null) {
            user = User.builder()
//                .id(profile.getId())
                .imageUrl(profile.getKakao_account().getProfile().getProfile_image_url())
                .username(profile.getKakao_account().getProfile().getNickname())
                .nickname(profile.getKakao_account().getProfile().getNickname())
                .email(profile.getKakao_account().getEmail())
                .authProvider("Kakao")
                .role("ROLE_USER").build();

            userRepository.save(user);
        }
        String userToken =  jwtUtils.generateToken(user);

        return UserLoginResponseDto.builder().userId(user.getId()).email(user.getEmail())
            .name(user.getUsername()).nickname(user.getNickname()).token(userToken).role(user.getRole())
            .imageUrl(user.getImageUrl()).authProvider("Kakao").build();

    }

    public KakaoProfile findProfileKakao(String token) {

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token); //(1-4)
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        HttpEntity<MultiValueMap<String, String>> kakaoProfileRequest = new HttpEntity<>(headers);

        ResponseEntity<String> kakaoProfileResponse = restTemplate.exchange(
            "https://kapi.kakao.com/v2/user/me",
            HttpMethod.POST,
            kakaoProfileRequest,
            String.class
        );

        ObjectMapper objectMapper = new ObjectMapper();
        KakaoProfile kakaoProfile = null;
        try {
            kakaoProfile = objectMapper.readValue(kakaoProfileResponse.getBody(), KakaoProfile.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return kakaoProfile;
    }
}

package com.feelmycode.parabole.controller;

import com.feelmycode.parabole.domain.User;
import com.feelmycode.parabole.dto.UserDto;
import com.feelmycode.parabole.global.api.ParaboleResponse;
import com.feelmycode.parabole.security.model.JwtProperties;
import com.feelmycode.parabole.security.model.OauthToken;
import com.feelmycode.parabole.security.utils.TokenProvider;
import com.feelmycode.parabole.service.UserService;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping
public class AuthController {

    private final UserService userService;
    private final TokenProvider tokenProvider;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<ParaboleResponse> registerUser(@RequestBody UserDto userDTO) {
        try {
            // 리퀘스트를 이용해 저장할 유저 만들기
            User user = User.builder()
                .email(userDTO.getEmail())
                .username(userDTO.getName())
                .nickname(userDTO.getNickname())
                .phone(userDTO.getPhone())
                .password(userDTO.getPassword())
                .build();
            // 서비스를 이용해 리파지토리에 유저 저장
            User registeredUser = userService.create(user);
            UserDto responseUserDTO = UserDto.builder()
                .email(registeredUser.getEmail())
                .id(registeredUser.getId())
                .name(registeredUser.getUsername())
                .build();

            return ParaboleResponse.CommonResponse(HttpStatus.OK, true, "기본 회원가입 성공",
                responseUserDTO);
        } catch (Exception e) {
            e.printStackTrace();
            return ParaboleResponse.CommonResponse(HttpStatus.BAD_REQUEST, false, "기본 회원가입 실패");
        }
    }

    @PostMapping("/api/v1/auth/signin")
    public ResponseEntity authenticate(@RequestBody UserDto userDTO) {
        User user = userService.getByCredentials(
            userDTO.getEmail(),
            userDTO.getPassword(), passwordEncoder
            );
        log.info(userDTO.getEmail());
        log.info(userDTO.getPassword());
        if (user != null) {
            // 토큰 생성
            final String token = tokenProvider.create(user);
            final UserDto responseUserDTO = UserDto.builder()
                .id(user.getId())
                .token(token)
                .email(user.getEmail())
                .name(user.getUsername())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .build();

            HttpHeaders headers = new HttpHeaders();
            headers.set(JwtProperties.HEADER_STRING, JwtProperties.TOKEN_PREFIX + token);

            return ResponseEntity.ok().headers(headers)
                .body(ParaboleResponse.CommonResponse(HttpStatus.OK, true, "기본 로그인 성공",
                    responseUserDTO));
        } else {
            return ParaboleResponse.CommonResponse(HttpStatus.BAD_REQUEST, false, "기본 로그인 실패");
        }
    }

    @GetMapping("/api/v1/auth/signout")
    public ResponseEntity logout() {
        HttpHeaders headers = new HttpHeaders();
        headers.clear();

        return ResponseEntity.ok().headers(headers)
            .body(ParaboleResponse.CommonResponse(HttpStatus.OK, true, "로그아웃 성공"));
    }

    // 프론트에서 인가코드 받아오는 url
    @GetMapping("/oauth2/code/kakao")
    public HttpServletResponse getKakaoLogin(@RequestParam(required = false) String code, HttpServletResponse httpServletResponse) throws IOException {

        // 넘어온 인가 코드를 통해 access_token 발급
        OauthToken oauthToken = userService.getAccessToken(code);
        // 발급 받은 accessToken 으로 카카오 회원 정보 DB 저장 후 JWT 를 생성
        String jwtToken = userService.saveUserAndGetToken(oauthToken.getAccess_token());

        String Front_URL = "http://localhost:3000";

        httpServletResponse.setHeader(JwtProperties.HEADER_STRING, JwtProperties.TOKEN_PREFIX + jwtToken);
        httpServletResponse.sendRedirect(Front_URL + "/oauthkakao?token=" + jwtToken);
        return httpServletResponse;
    }

}

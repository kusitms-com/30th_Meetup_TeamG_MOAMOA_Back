package corecord.dev.domain.user.service;

import corecord.dev.common.exception.GeneralException;
import corecord.dev.common.status.ErrorStatus;
import corecord.dev.common.util.CookieUtil;
import corecord.dev.common.util.JwtUtil;
import corecord.dev.domain.record.repository.RecordRepository;
import corecord.dev.domain.token.entity.RefreshToken;
import corecord.dev.domain.token.exception.enums.TokenErrorStatus;
import corecord.dev.domain.token.exception.model.TokenException;
import corecord.dev.domain.token.repository.RefreshTokenRepository;
import corecord.dev.domain.user.converter.UserConverter;
import corecord.dev.domain.user.dto.request.UserRequest;
import corecord.dev.domain.user.dto.response.UserResponse;
import corecord.dev.domain.user.entity.Status;
import corecord.dev.domain.user.entity.User;
import corecord.dev.domain.user.exception.enums.UserErrorStatus;
import corecord.dev.domain.user.exception.model.UserException;
import corecord.dev.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final UserRepository userRepository;
    private final RecordRepository recordRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.access-token.expiration-time}")
    private long accessTokenExpirationTime;

    @Value("${jwt.refresh-token.expiration-time}")
    private long refreshTokenExpirationTime;

    @Transactional
    public UserResponse.UserDto registerUser(HttpServletResponse response, String registerToken, UserRequest.UserRegisterDto userRegisterDto) {
        // registerToken 유효성 검증
        validRegisterToken(registerToken);

        // user 정보 유효성 검증
        validateUserInfo(userRegisterDto.getNickName());

        String providerId = jwtUtil.getProviderIdFromToken(registerToken);
        // 이미 존재하는 유저인지 확인
        checkExistUser(providerId);

        // 새로운 유저 생성
        User newUser = UserConverter.toUserEntity(userRegisterDto, providerId);
        User savedUser = userRepository.save(newUser);

        // RefreshToken 생성 및 저장
        String refreshToken = jwtUtil.generateRefreshToken(savedUser.getUserId());
        saveRefreshToken(refreshToken, savedUser);

        // AccessToken 및 RefreshToken 쿠키 설정
        setTokenCookies(response, "accessToken", jwtUtil.generateAccessToken(savedUser.getUserId()));
        setTokenCookies(response, "refreshToken", refreshToken);
        return UserConverter.toUserDto(savedUser);
    }

    // 로그아웃
    @Transactional
    public void logoutUser(HttpServletRequest request, HttpServletResponse response) {
        // RefreshToken 삭제
        deleteRefreshTokenInRedis(request);
        deleteTokenCookies(response);
    }

    // 유저 삭제
    @Transactional
    public void deleteUser(HttpServletRequest request, HttpServletResponse response, Long userId) {
        // 유저 삭제
        userRepository.deleteById(userId);

        // RefreshToken 삭제
        deleteRefreshTokenInRedis(request);
        deleteTokenCookies(response);
    }

    // 유저 정보 수정
    @Transactional
    public void updateUser(Long userId, UserRequest.UserUpdateDto userUpdateDto) {
        User user = getUser(userId);

        if(userUpdateDto.getNickName() != null) {
            validateUserInfo(userUpdateDto.getNickName());
            user.setNickName(userUpdateDto.getNickName());
        }

        if(userUpdateDto.getStatus() != null) {
            user.setStatus(Status.getStatus(userUpdateDto.getStatus()));
        }
    }

    // 유저 정보 조회
    @Transactional
    public UserResponse.UserInfoDto getUserInfo(Long userId) {
        User user = getUser(userId);

        int recordCount = getRecordCount(user);
        return UserConverter.toUserInfoDto(user, recordCount);
    }

    private int getRecordCount(User user) {
        int recordCount = recordRepository.getRecordCount(user);
        if (user.getTmpChat() != null) {
            recordCount--;
        }
        if (user.getTmpMemo() != null) {
            recordCount--;
        }
        return recordCount;
    }

    // RefreshToken 저장
    private void saveRefreshToken(String refreshToken, User user) {
        RefreshToken newRefreshToken = RefreshToken.of(refreshToken, user.getUserId());
        refreshTokenRepository.save(newRefreshToken);
    }

    // 토큰 쿠키 설정
    private void setTokenCookies(HttpServletResponse response, String tokenName, String token) {
        if(tokenName.equals("accessToken")) {
            ResponseCookie accessTokenCookie = cookieUtil.createTokenCookie(tokenName, token, accessTokenExpirationTime);
            response.addHeader("Set-Cookie", accessTokenCookie.toString());
        } else {
            ResponseCookie refreshTokenCookie = cookieUtil.createTokenCookie(tokenName, token, refreshTokenExpirationTime);
            response.addHeader("Set-Cookie", refreshTokenCookie.toString());
        }
    }

    private void deleteTokenCookies(HttpServletResponse response) {
        ResponseCookie accessTokenCookie = cookieUtil.deleteCookie("accessToken");
        response.addHeader("Set-Cookie", accessTokenCookie.toString());
        ResponseCookie refreshTokenCookie = cookieUtil.deleteCookie("refreshToken");
        response.addHeader("Set-Cookie", refreshTokenCookie.toString());
    }

    private void deleteRefreshTokenInRedis(HttpServletRequest request) {
        Optional<RefreshToken> refreshTokenOptional = refreshTokenRepository.findByRefreshToken(cookieUtil.getCookieValue(request, "refreshToken"));
        refreshTokenOptional.ifPresent(refreshTokenRepository::delete);
    }

    private void checkExistUser(String providerId) {
        if (userRepository.existsByProviderId(providerId)) {
            throw new UserException(UserErrorStatus.ALREADY_EXIST_USER);
        }
    }

    // user 정보 유효성 검증
    private void validateUserInfo(String nickName) {
        if (nickName == null || nickName.isEmpty() || nickName.length() > 10) {
            throw new UserException(UserErrorStatus.INVALID_USER_NICKNAME);
        }

        // 한글, 영어, 숫자, 공백만 허용
        String nicknamePattern = "^[a-zA-Z0-9가-힣\\s]*$";
        if (!Pattern.matches(nicknamePattern, nickName)) {
            throw new UserException(UserErrorStatus.INVALID_USER_NICKNAME);
        }
    }

    // registerToken 유효성 검증
    private void validRegisterToken(String registerToken) {
        if (!jwtUtil.isRegisterTokenValid(registerToken)) {
            throw new TokenException(TokenErrorStatus.INVALID_REGISTER_TOKEN);
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.UNAUTHORIZED));
    }
}

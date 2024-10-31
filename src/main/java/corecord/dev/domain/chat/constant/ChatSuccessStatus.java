package corecord.dev.domain.chat.constant;

import corecord.dev.common.base.BaseSuccessStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChatSuccessStatus implements BaseSuccessStatus {

    CHAT_ROOM_CREATE_SUCCESS(HttpStatus.CREATED, "S301", "채팅방 생성이 성공적으로 완료되었습니다."),
    CHAT_CREATE_SUCCESS(HttpStatus.CREATED, "S302", "채팅 생성이 성공적으로 완료되었습니다."),
    GET_CHAT_SUCCESS(HttpStatus.OK, "S303", "채팅 조회가 성공적으로 완료되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
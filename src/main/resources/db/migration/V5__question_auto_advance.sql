-- 문항 자동 넘김 (2026-09-07, 와이어프레임 W-02b 대조 결정 — 어제 B-19 "넣지 않는다"를 뒤집음)
-- W-02b 문항별 시간 설정에 문항마다 "자동 넘김" 토글이 있다. 방 단위 설정이라 시간 오버라이드와 같은 자리에 둔다.
-- 세션 시작 시 session_question 으로 복사되고, 시간 만료로 마감된 문항은 잠시 뒤 서버가 다음 문항을 자동 개시한다.
-- V1~V4 는 이미 적용됐으므로 고치지 않고 여기에 변경분만 쌓는다.

ALTER TABLE room
  ADD COLUMN question_auto_advance JSON NULL
    COMMENT '자동 넘김을 켠 문항 id 배열. NULL = 전부 꺼짐'
    AFTER question_time_overrides;

ALTER TABLE session_question
  ADD COLUMN auto_advance BOOLEAN NOT NULL DEFAULT FALSE
    COMMENT '시간 만료 마감 후 다음 문항 자동 개시 여부. 세션 시작 시 방 설정에서 복사'
    AFTER time_limit_sec;

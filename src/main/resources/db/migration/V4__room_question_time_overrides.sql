-- 방 단위 문항별 제한시간 오버라이드 (2026-09-07, 웹 버그 리포트 B-18 결정 (b))
-- 확정 세트는 불변이라 W-02b "문항별 시간 설정"이 세트를 고칠 수 없다. 대신 방이 questionId → 초 를 덮어쓰고
-- 세션 시작 시 session_question.time_limit_sec 로 복사한다. 세트를 바꾸면 비운다(예전 세트의 문항 id 를 가리키므로).
-- V1~V3 은 이미 적용됐으므로 고치지 않고 여기에 변경분만 쌓는다.

ALTER TABLE room
  ADD COLUMN question_time_overrides JSON NULL
    COMMENT '이 방에서만 쓰는 문항별 제한시간 {questionId: sec}. NULL = 세트 기본값'
    AFTER question_set_id;

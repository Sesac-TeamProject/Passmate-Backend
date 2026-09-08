-- 자동 넘김 기본값을 ON 으로 반전 (2026-09-08 시나리오 테스트 — "시간이 끝나면 바로 다음 문제로").
-- 저장 의미가 "켠 목록" → "끈 목록"으로 뒤집히므로 컬럼 이름도 함께 바꾼다.
-- 전체 교체 PUT 에서 본문에 빠진 문항이 기본값(켬)으로 돌아가려면 예외 쪽(끈 문항)을 저장해야 한다.
-- 기존 값은 초기화한다 — 반전 전에 저장된 "켠 목록"은 새 의미로 읽으면 정반대가 되기 때문.
-- V1~V6 은 이미 적용됐으므로 고치지 않고 여기에 변경분만 쌓는다.

ALTER TABLE room RENAME COLUMN question_auto_advance TO question_auto_advance_off;
UPDATE room SET question_auto_advance_off = NULL;
ALTER TABLE room
  MODIFY question_auto_advance_off JSON NULL
    COMMENT '자동 넘김을 끈 문항 id 배열. NULL = 전부 켬(기본)';

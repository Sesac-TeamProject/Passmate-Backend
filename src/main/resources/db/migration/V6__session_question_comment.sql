-- 문항 단위 선생님 코멘트 (2026-09-07, 와이어프레임 W-07 우측 패널 · 웹 QA_BACKLOG B-14)
-- "학생 전체에게 남길 첨삭" — 답안(학생)별 첨삭(teacher_review)과 별개로 문항 하나에 한 장.
-- 학생 쪽은 M-06 "선생님 코멘트가 도착하면 여기에 표시돼요" 자리에 내려간다.

CREATE TABLE session_question_comment (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  session_question_id BIGINT NOT NULL,
  reviewer_user_id BIGINT NOT NULL,
  comment TEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_sqc_question (session_question_id),
  CONSTRAINT fk_sqc_sq FOREIGN KEY (session_question_id) REFERENCES session_question(id),
  CONSTRAINT fk_sqc_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='문항 단위 선생님 코멘트 — 학생 전체 대상(W-07)';

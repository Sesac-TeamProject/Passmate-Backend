-- 서술형 AI 분석 코인화 (2026-09-01 결정)
-- 학생이 자기 답안의 AI 분석을 볼 때 월 무료 한도를 쓰고, 넘으면 코인을 차감한다.
-- V1 은 이미 적용됐으므로 고치지 않고 여기에 변경분만 쌓는다.

-- 분석 요청·결제 주체. answer -> participant 로 따라갈 수 있지만,
-- 월 무료 한도를 세려면 매번 3단 조인을 해야 해서 비정규화한다.
ALTER TABLE ai_feedback
  ADD COLUMN user_id BIGINT NOT NULL COMMENT '분석을 요청·결제한 회원. 월 무료 한도 집계를 위해 비정규화' AFTER answer_id,
  ADD COLUMN charged_coins INT NOT NULL DEFAULT 0 COMMENT '차감한 코인. 0 = 월 무료 한도 사용분' AFTER status,
  ADD KEY idx_ai_feedback_user (user_id, created_at),
  ADD CONSTRAINT fk_ai_feedback_user FOREIGN KEY (user_id) REFERENCES `user`(id);

-- 원장에 AI 분석 차감 종류를 추가한다(컬럼은 VARCHAR 라 값만 늘어난다).
ALTER TABLE coin_transaction
  MODIFY COLUMN type VARCHAR(20) NOT NULL COMMENT 'CHARGE/ENTRY/REFUND/AI_ANALYSIS/ADMIN_ADJUST',
  MODIFY COLUMN ref_type VARCHAR(20) NULL COMMENT 'COIN_CHARGE/ENTRY_PAYMENT/AI_FEEDBACK';

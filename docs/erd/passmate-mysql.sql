-- PassMate ERD v2 — MySQL 8.0 DDL (2026-08-28, 피그마 v6 확정 화면 반영)
-- ERDCloud / drawSQL / dbdiagram.io "Import from MySQL" 용. Flyway V1 초안으로도 사용 가능.
-- 규칙: ENUM 대신 VARCHAR, 금액 INT(원 또는 코인, 1 C = ₩1), DATETIME(6) UTC, soft delete(user·question_set)
-- v2 변경: payment → coin_wallet/coin_charge(포트원)/entry_payment/coin_transaction, user_block·notification_setting·device_token 추가,
--          user.default_avatar_id, room.topic/max_participants/screen_locked, room_rating.tags, 회원가입·로그인 Google 단일

SET NAMES utf8mb4;

CREATE TABLE `user` (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  provider VARCHAR(20) NOT NULL COMMENT 'GOOGLE (Google 단일 로그인, 2026-08-28)',
  provider_id VARCHAR(100) NOT NULL,
  email VARCHAR(255) NULL,
  nickname VARCHAR(30) NOT NULL,
  profile_image_url VARCHAR(500) NULL,
  default_avatar_id VARCHAR(30) NULL COMMENT '기본 캐릭터, 입장 시 participant.avatar_id 기본값',
  is_admin BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(20) NOT NULL COMMENT 'ACTIVE/SUSPENDED/DELETED',
  last_login_at DATETIME(6) NULL,
  deleted_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_user_provider (provider, provider_id),
  KEY idx_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='소셜 계정 단일 회원';

CREATE TABLE badge (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(300) NULL,
  condition_type VARCHAR(30) NULL,
  condition_value INT NULL,
  icon_url VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_badge_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE question_set (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL COMMENT 'DRAFT/CONFIRMED',
  source VARCHAR(20) NULL COMMENT 'AI/MANUAL/MIXED',
  question_count INT NOT NULL DEFAULT 0,
  total_points INT NOT NULL DEFAULT 0,
  estimated_seconds INT NULL,
  usage_count INT NOT NULL DEFAULT 0,
  last_used_at DATETIME(6) NULL,
  confirmed_at DATETIME(6) NULL,
  duplicated_from_id BIGINT NULL,
  deleted_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  KEY idx_qs_owner (owner_user_id, status, deleted_at),
  CONSTRAINT fk_qs_owner FOREIGN KEY (owner_user_id) REFERENCES `user`(id),
  CONSTRAINT fk_qs_dup FOREIGN KEY (duplicated_from_id) REFERENCES question_set(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='확정 후 불변';

CREATE TABLE question (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  set_id BIGINT NOT NULL,
  order_no INT NOT NULL,
  type VARCHAR(10) NOT NULL COMMENT 'MCQ/OX/ESSAY',
  content TEXT NOT NULL,
  choices JSON NULL,
  answer VARCHAR(500) NULL,
  explanation TEXT NULL,
  topic VARCHAR(100) NULL,
  difficulty VARCHAR(10) NULL,
  time_limit_sec INT NOT NULL,
  points INT NOT NULL,
  source VARCHAR(10) NOT NULL COMMENT 'AI/MANUAL',
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_question_order (set_id, order_no),
  KEY idx_question_topic (topic),
  CONSTRAINT fk_question_set FOREIGN KEY (set_id) REFERENCES question_set(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_generation_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  set_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL COMMENT 'SET/REGENERATE/FILE',
  params JSON NULL,
  status VARCHAR(20) NOT NULL COMMENT 'SUCCESS/FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(500) NULL,
  model VARCHAR(50) NULL,
  duration_ms INT NULL,
  created_at DATETIME(6) NOT NULL,
  KEY idx_agl_user (user_id, created_at),
  CONSTRAINT fk_agl_set FOREIGN KEY (set_id) REFERENCES question_set(id),
  CONSTRAINT fk_agl_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE room (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  host_user_id BIGINT NOT NULL,
  question_set_id BIGINT NULL,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  topic VARCHAR(50) NULL COMMENT '주제 태그(백엔드/CS 면접/네트워크 …)',
  pin CHAR(6) NOT NULL COMMENT '활성 방 간에만 유일(Redis), 종료 후 재사용',
  status VARCHAR(20) NOT NULL COMMENT 'WAITING/RUNNING/ENDED/CANCELED',
  type VARCHAR(20) NOT NULL COMMENT 'FREE/PAID/BRANDED',
  fee INT NULL COMMENT '참가비(코인, 1C=1원)',
  max_participants INT NULL COMMENT 'NULL = 제한 없음',
  is_public BOOLEAN NOT NULL DEFAULT FALSE,
  scheduled_at DATETIME(6) NULL,
  started_at DATETIME(6) NULL,
  ended_at DATETIME(6) NULL,
  current_question_no INT NOT NULL DEFAULT 0,
  screen_locked BOOLEAN NOT NULL DEFAULT FALSE COMMENT '학생 화면 잠금(M-T2)',
  participant_count INT NOT NULL DEFAULT 0,
  avg_score DECIMAL(7,2) NULL,
  correct_rate DECIMAL(5,2) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  KEY idx_room_pin (pin, status),
  KEY idx_room_host (host_user_id, status),
  KEY idx_room_public (status, is_public, scheduled_at),
  KEY idx_room_ended (ended_at),
  CONSTRAINT fk_room_host FOREIGN KEY (host_user_id) REFERENCES `user`(id),
  CONSTRAINT fk_room_set FOREIGN KEY (question_set_id) REFERENCES question_set(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='방 = 세션 1회, 호스트가 직접 종료';

CREATE TABLE session_question (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  room_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  order_no INT NOT NULL,
  time_limit_sec INT NOT NULL,
  started_at DATETIME(6) NULL,
  ends_at DATETIME(6) NULL,
  ended_at DATETIME(6) NULL,
  submit_count INT NOT NULL DEFAULT 0,
  correct_count INT NOT NULL DEFAULT 0,
  correct_rate DECIMAL(5,2) NULL,
  answer_distribution JSON NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_sq_order (room_id, order_no),
  KEY idx_sq_question (question_id),
  CONSTRAINT fk_sq_room FOREIGN KEY (room_id) REFERENCES room(id),
  CONSTRAINT fk_sq_question FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE voice_hint (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  room_id BIGINT NOT NULL,
  session_question_id BIGINT NOT NULL,
  audio_url VARCHAR(500) NOT NULL,
  duration_ms INT NULL,
  published_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_vh_room FOREIGN KEY (room_id) REFERENCES room(id),
  CONSTRAINT fk_vh_sq FOREIGN KEY (session_question_id) REFERENCES session_question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE participant (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  room_id BIGINT NOT NULL,
  user_id BIGINT NULL COMMENT 'NULL = 게스트',
  guest_token VARCHAR(64) NULL,
  device_key VARCHAR(64) NULL,
  nickname VARCHAR(30) NOT NULL,
  avatar_id VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL COMMENT 'JOINED/LEFT/KICKED',
  joined_at DATETIME(6) NOT NULL,
  left_at DATETIME(6) NULL,
  total_score INT NOT NULL DEFAULT 0,
  final_rank INT NULL,
  claimed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_participant_nickname (room_id, nickname),
  UNIQUE KEY uk_participant_guest (guest_token),
  KEY idx_participant_user (user_id),
  KEY idx_participant_room_status (room_id, status),
  CONSTRAINT fk_participant_room FOREIGN KEY (room_id) REFERENCES room(id),
  CONSTRAINT fk_participant_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_block (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  blocker_user_id BIGINT NOT NULL,
  blocked_user_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_user_block (blocker_user_id, blocked_user_id),
  CONSTRAINT fk_ub_blocker FOREIGN KEY (blocker_user_id) REFERENCES `user`(id),
  CONSTRAINT fk_ub_blocked FOREIGN KEY (blocked_user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='호스트 차단 — 차단한 호스트의 방은 목록에서 제외';

CREATE TABLE notification_setting (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  session_start BOOLEAN NOT NULL DEFAULT TRUE,
  rating_request BOOLEAN NOT NULL DEFAULT TRUE,
  settlement_done BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_notification_setting_user (user_id),
  CONSTRAINT fk_ns_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE device_token (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  platform VARCHAR(10) NOT NULL COMMENT 'ANDROID/IOS/WEB',
  token VARCHAR(255) NOT NULL,
  last_seen_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_device_token (token),
  KEY idx_device_token_user (user_id),
  CONSTRAINT fk_dt_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='푸시 토큰(FCM/APNs)';

CREATE TABLE answer (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  participant_id BIGINT NOT NULL,
  session_question_id BIGINT NOT NULL,
  submitted TEXT NOT NULL,
  is_correct BOOLEAN NULL,
  remaining_ratio DECIMAL(5,4) NULL,
  base_score INT NOT NULL DEFAULT 0,
  speed_bonus INT NOT NULL DEFAULT 0,
  score INT NOT NULL DEFAULT 0,
  final_score INT NOT NULL DEFAULT 0,
  submitted_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_answer (participant_id, session_question_id),
  KEY idx_answer_sq (session_question_id),
  CONSTRAINT fk_answer_participant FOREIGN KEY (participant_id) REFERENCES participant(id),
  CONSTRAINT fk_answer_sq FOREIGN KEY (session_question_id) REFERENCES session_question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  answer_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL COMMENT 'PENDING/DONE/FAILED',
  key_points JSON NULL,
  missing_points JSON NULL,
  suggestions JSON NULL,
  summary TEXT NULL,
  model VARCHAR(50) NULL,
  latency_ms INT NULL,
  error_message VARCHAR(500) NULL,
  completed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_ai_feedback_answer (answer_id),
  CONSTRAINT fk_ai_feedback_answer FOREIGN KEY (answer_id) REFERENCES answer(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teacher_review (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  answer_id BIGINT NOT NULL,
  reviewer_user_id BIGINT NOT NULL,
  comment TEXT NULL,
  adjusted_score INT NULL,
  improvement TEXT NULL,
  reviewed_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_teacher_review_answer (answer_id),
  CONSTRAINT fk_tr_answer FOREIGN KEY (answer_id) REFERENCES answer(id),
  CONSTRAINT fk_tr_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE participant_report (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  participant_id BIGINT NOT NULL,
  total_questions INT NOT NULL,
  correct_count INT NOT NULL,
  accuracy DECIMAL(5,2) NOT NULL,
  total_score INT NOT NULL,
  final_rank INT NOT NULL,
  weak_topics JSON NULL,
  generated_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_participant_report (participant_id),
  CONSTRAINT fk_pr_participant FOREIGN KEY (participant_id) REFERENCES participant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE room_rating (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  room_id BIGINT NOT NULL,
  participant_id BIGINT NOT NULL,
  host_user_id BIGINT NOT NULL,
  stars TINYINT NOT NULL,
  tags JSON NULL COMMENT '설명 명확/난이도 적당/시간 배분/힌트 도움/문제 품질 다중 선택',
  comment VARCHAR(500) NULL COMMENT '한 줄 후기, 호스트에게만 공개',
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_room_rating (room_id, participant_id),
  KEY idx_room_rating_host (host_user_id),
  CONSTRAINT chk_room_rating_stars CHECK (stars BETWEEN 1 AND 5),
  CONSTRAINT fk_rr_room FOREIGN KEY (room_id) REFERENCES room(id),
  CONSTRAINT fk_rr_participant FOREIGN KEY (participant_id) REFERENCES participant(id),
  CONSTRAINT fk_rr_host FOREIGN KEY (host_user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE host_profile (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  level TINYINT NOT NULL DEFAULT 1,
  level_achieved_at DATETIME(6) NULL,
  rooms_hosted INT NOT NULL DEFAULT 0,
  sessions_completed INT NOT NULL DEFAULT 0,
  total_students INT NOT NULL DEFAULT 0,
  avg_rating DECIMAL(3,2) NULL,
  rating_count INT NOT NULL DEFAULT 0,
  active_last_30d INT NOT NULL DEFAULT 0,
  last_evaluated_at DATETIME(6) NULL,
  next_evaluation_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_host_profile_user (user_id),
  CONSTRAINT fk_hp_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE level_history (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  from_level TINYINT NULL,
  to_level TINYINT NOT NULL,
  reason VARCHAR(200) NULL,
  evaluated_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_lh_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_badge (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  badge_id BIGINT NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  achieved_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_user_badge (user_id, badge_id),
  CONSTRAINT fk_ub_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_ub_badge FOREIGN KEY (badge_id) REFERENCES badge(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE settlement (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  host_user_id BIGINT NOT NULL,
  period VARCHAR(7) NOT NULL COMMENT 'YYYY-MM',
  amount INT NOT NULL,
  status VARCHAR(20) NOT NULL COMMENT 'PENDING/COMPLETED/HELD',
  scheduled_at DATE NULL,
  paid_at DATETIME(6) NULL,
  memo VARCHAR(500) NULL,
  processed_by_user_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_settlement_period (host_user_id, period),
  CONSTRAINT fk_settlement_host FOREIGN KEY (host_user_id) REFERENCES `user`(id),
  CONSTRAINT fk_settlement_processor FOREIGN KEY (processed_by_user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE host_earning (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  room_id BIGINT NOT NULL,
  host_user_id BIGINT NOT NULL,
  participant_count INT NOT NULL,
  gross INT NOT NULL,
  platform_fee INT NOT NULL,
  net INT NOT NULL,
  status VARCHAR(20) NOT NULL COMMENT 'PENDING/SETTLED/HELD/CARRIED',
  settlement_id BIGINT NULL,
  earned_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_host_earning_room (room_id),
  KEY idx_host_earning_host (host_user_id, status),
  CONSTRAINT fk_he_room FOREIGN KEY (room_id) REFERENCES room(id),
  CONSTRAINT fk_he_host FOREIGN KEY (host_user_id) REFERENCES `user`(id),
  CONSTRAINT fk_he_settlement FOREIGN KEY (settlement_id) REFERENCES settlement(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE settlement_account (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  bank_code VARCHAR(10) NOT NULL,
  bank_name VARCHAR(50) NOT NULL,
  account_no_enc VARCHAR(255) NOT NULL,
  holder_name VARCHAR(50) NOT NULL,
  verified_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_settlement_account_user (user_id),
  CONSTRAINT fk_sa_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coin_wallet (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  balance INT NOT NULL DEFAULT 0 COMMENT '보유 코인, 1C=1원',
  default_payment_method VARCHAR(30) NULL COMMENT 'KAKAOPAY/NAVERPAY/TOSSPAY/CARD/BANK_TRANSFER',
  last_transaction_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_coin_wallet_user (user_id),
  CONSTRAINT chk_coin_wallet_balance CHECK (balance >= 0),
  CONSTRAINT fk_cw_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='첫 로그인 시 생성. balance 변경은 coin_transaction과 같은 트랜잭션';

CREATE TABLE coin_charge (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  room_id BIGINT NULL COMMENT '충전 직후 참가비 차감할 방(선택)',
  amount INT NOT NULL COMMENT '충전 코인 = 결제 원화',
  method VARCHAR(30) NULL COMMENT 'KAKAOPAY/NAVERPAY/TOSSPAY/CARD/BANK_TRANSFER',
  pg_provider VARCHAR(20) NOT NULL DEFAULT 'PORTONE',
  merchant_uid VARCHAR(64) NOT NULL COMMENT '우리가 발급한 주문 ID(paymentId)',
  pg_payment_id VARCHAR(100) NULL COMMENT '포트원 결제 ID(imp_uid)',
  pg_payload JSON NULL,
  status VARCHAR(20) NOT NULL COMMENT 'READY/PAID/FAILED/CANCELED',
  paid_at DATETIME(6) NULL,
  canceled_at DATETIME(6) NULL,
  cancel_reason VARCHAR(200) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_coin_charge_merchant (merchant_uid),
  UNIQUE KEY uk_coin_charge_pg (pg_payment_id),
  KEY idx_coin_charge_user (user_id, status),
  CONSTRAINT fk_cc_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_cc_room FOREIGN KEY (room_id) REFERENCES room(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='포트원 코인 충전 건. confirm/웹훅 멱등';

CREATE TABLE entry_payment (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  payment_no VARCHAR(24) NOT NULL COMMENT 'PM-YYYY-MMDD-NNNN 영수증 번호',
  room_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  participant_id BIGINT NULL COMMENT '입장 완료 후 연결',
  amount INT NOT NULL COMMENT '차감 코인',
  status VARCHAR(20) NOT NULL COMMENT 'PAID/REFUNDED',
  paid_at DATETIME(6) NOT NULL,
  refunded_at DATETIME(6) NULL,
  refund_reason VARCHAR(200) NULL COMMENT '학생 취소/방 취소/호스트 사유 미진행/강퇴/관리자',
  refunded_by_user_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_entry_payment_no (payment_no),
  KEY idx_entry_payment_room_user (room_id, user_id),
  KEY idx_entry_payment_user (user_id, status),
  CONSTRAINT fk_ep_room FOREIGN KEY (room_id) REFERENCES room(id),
  CONSTRAINT fk_ep_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_ep_participant FOREIGN KEY (participant_id) REFERENCES participant(id),
  CONSTRAINT fk_ep_refunder FOREIGN KEY (refunded_by_user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='참가비 코인 차감. 세션 시작 전 취소는 코인 100% 환급';

CREATE TABLE coin_transaction (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL COMMENT 'CHARGE/ENTRY/REFUND/ADMIN_ADJUST',
  amount INT NOT NULL COMMENT '부호 있음: +충전·환급, -차감',
  balance_after INT NOT NULL,
  ref_type VARCHAR(20) NULL COMMENT 'COIN_CHARGE/ENTRY_PAYMENT',
  ref_id BIGINT NULL,
  memo VARCHAR(200) NULL,
  created_at DATETIME(6) NOT NULL,
  KEY idx_coin_tx_user (user_id, created_at),
  KEY idx_coin_tx_ref (ref_type, ref_id),
  CONSTRAINT fk_ct_user FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='코인 원장(append-only)';

CREATE TABLE sanction (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL,
  guest_key VARCHAR(64) NULL,
  type VARCHAR(20) NOT NULL COMMENT 'SUSPEND/WARN/JOIN_BAN/PUBLISH_BAN',
  reason VARCHAR(500) NOT NULL,
  starts_at DATETIME(6) NULL,
  ends_at DATETIME(6) NULL,
  status VARCHAR(20) NOT NULL COMMENT 'ACTIVE/RELEASED/EXPIRED',
  issued_by_user_id BIGINT NOT NULL,
  released_at DATETIME(6) NULL,
  release_reason VARCHAR(200) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  KEY idx_sanction_user (user_id, status),
  KEY idx_sanction_guest (guest_key, status),
  CONSTRAINT fk_sanction_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_sanction_issuer FOREIGN KEY (issued_by_user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE report (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  reporter_user_id BIGINT NULL,
  reporter_participant_id BIGINT NULL,
  target_type VARCHAR(20) NOT NULL COMMENT 'USER/PARTICIPANT/QUESTION/ROOM',
  target_id BIGINT NOT NULL,
  type VARCHAR(30) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL COMMENT 'OPEN/REVIEWING/RESOLVED',
  handled_by_user_id BIGINT NULL,
  handled_at DATETIME(6) NULL,
  resolution_memo VARCHAR(500) NULL,
  sanction_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  KEY idx_report_status (status, created_at),
  KEY idx_report_target (target_type, target_id),
  CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_user_id) REFERENCES `user`(id),
  CONSTRAINT fk_report_reporter_participant FOREIGN KEY (reporter_participant_id) REFERENCES participant(id),
  CONSTRAINT fk_report_handler FOREIGN KEY (handled_by_user_id) REFERENCES `user`(id),
  CONSTRAINT fk_report_sanction FOREIGN KEY (sanction_id) REFERENCES sanction(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE question_review (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  question_id BIGINT NOT NULL,
  trigger_type VARCHAR(20) NOT NULL COMMENT 'REPORT/LOW_ACCURACY/HIGH_ACCURACY',
  status VARCHAR(20) NOT NULL COMMENT 'PENDING/APPROVED/REJECTED',
  decision_memo VARCHAR(500) NULL,
  reviewed_by_user_id BIGINT NULL,
  reviewed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  CONSTRAINT fk_qr_question FOREIGN KEY (question_id) REFERENCES question(id),
  CONSTRAINT fk_qr_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ad_campaign (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  advertiser VARCHAR(100) NOT NULL,
  placement VARCHAR(30) NOT NULL COMMENT 'RESULT_BOTTOM/WAITING_ROOM_BANNER/REPORT_BOTTOM/HOME_CARD',
  creative_url VARCHAR(500) NOT NULL,
  link_url VARCHAR(500) NOT NULL,
  starts_at DATE NOT NULL,
  ends_at DATE NOT NULL,
  status VARCHAR(20) NOT NULL COMMENT 'PENDING_REVIEW/ACTIVE/ENDED',
  contract_amount INT NULL,
  impressions INT NOT NULL DEFAULT 0,
  clicks INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  KEY idx_ad_campaign_placement (placement, status, starts_at, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ad_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  campaign_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL COMMENT 'IMPRESSION/CLICK',
  user_id BIGINT NULL,
  participant_id BIGINT NULL,
  occurred_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  KEY idx_ad_event_campaign (campaign_id, type, occurred_at),
  CONSTRAINT fk_ae_campaign FOREIGN KEY (campaign_id) REFERENCES ad_campaign(id),
  CONSTRAINT fk_ae_user FOREIGN KEY (user_id) REFERENCES `user`(id),
  CONSTRAINT fk_ae_participant FOREIGN KEY (participant_id) REFERENCES participant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE branded_quiz (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  room_id BIGINT NOT NULL,
  company VARCHAR(100) NOT NULL,
  purpose VARCHAR(20) NULL COMMENT 'RECRUIT/BRAND/EDUCATION',
  contract_amount INT NULL,
  exposure_start DATE NULL,
  exposure_end DATE NULL,
  delegated_host_user_id BIGINT NULL,
  status VARCHAR(20) NOT NULL COMMENT 'PRODUCING/RUNNING/ENDED',
  participant_count INT NOT NULL DEFAULT 0,
  completion_rate DECIMAL(5,2) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_branded_quiz_room (room_id),
  CONSTRAINT fk_bq_room FOREIGN KEY (room_id) REFERENCES room(id),
  CONSTRAINT fk_bq_host FOREIGN KEY (delegated_host_user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin_activity_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  type VARCHAR(30) NOT NULL,
  actor_user_id BIGINT NULL,
  ref_type VARCHAR(30) NULL,
  ref_id BIGINT NULL,
  summary VARCHAR(200) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  KEY idx_aal_occurred (occurred_at),
  CONSTRAINT fk_aal_actor FOREIGN KEY (actor_user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

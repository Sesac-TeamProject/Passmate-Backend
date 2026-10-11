-- Apple 로그인 토큰 폐기(App Store 심사 지침 5.1.1(v) — 탈퇴하면 Apple ID 연결도 끊어야 한다).
-- 폐기 API 는 refresh token 을 요구하는데, 그건 로그인 때 앱이 준 인가 코드를 교환해야만 받는다. 그래서 보관한다.
-- 값은 AES-256-GCM 으로 암호화해 저장한다(TextEncryptor, 정산 계좌번호와 같은 키). 탈퇴하면 지운다.

ALTER TABLE `user`
  ADD COLUMN apple_refresh_token VARCHAR(512) NULL
    COMMENT 'Apple refresh token (암호문). 탈퇴 시 폐기 후 삭제' AFTER default_avatar_id,
  ADD COLUMN apple_client_id VARCHAR(100) NULL
    COMMENT '토큰이 발급된 클라이언트 ID(앱 번들 ID). 폐기 요청에 같은 값을 써야 한다' AFTER apple_refresh_token;

ALTER TABLE `user`
  MODIFY provider VARCHAR(20) NOT NULL COMMENT 'GOOGLE / APPLE(iOS)';

-- 탈퇴한 계정의 소셜 연결 해제.
-- 구글·Apple 은 같은 사람에게 같은 sub 를 계속 준다. 탈퇴 행이 원래 provider_id 를 쥐고 있으면
-- 같은 계정으로 다시 로그인할 때 그 행이 잡혀(404 "탈퇴한 계정입니다") 재가입할 수 없고,
-- uk_user_provider 때문에 새 행도 만들 수 없다. 앞으로의 탈퇴는 User.withdraw() 가 같은 값으로 바꾼다.

UPDATE `user`
   SET provider_id = CONCAT('withdrawn:', id)
 WHERE status = 'DELETED'
   AND provider_id NOT LIKE 'withdrawn:%';

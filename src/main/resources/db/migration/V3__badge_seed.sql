-- 뱃지 8종 (기능 명세서 FR-048). 화면이 컬렉션으로 그리는 고정 목록이라 기준 데이터로 넣는다.
-- condition_value 는 INT 라 별점만 10배로 담는다 (45 = 4.5). BadgeConditionType 이 되돌려 읽는다.
INSERT INTO badge (code, name, description, condition_type, condition_value, created_at) VALUES
  ('FIRST_ROOM',      '첫 방 개설',      '방을 만들고 첫 세션을 진행했다',            'ROOMS_HOSTED',       1,  NOW(6)),
  ('ROOMS_10',        '방 10회 운영',    '세션을 10번 진행했다',                      'ROOMS_HOSTED',       10, NOW(6)),
  ('STUDENTS_100',    '학생 100명',      '누적 100명의 학생을 만났다',                'TOTAL_STUDENTS',     100, NOW(6)),
  ('RATING_45',       '평가 4.5+',       '평균 별점 4.5 이상을 지켰다',               'AVG_RATING',         45, NOW(6)),
  ('RATINGS_50',      '평가 50개 받기',  '학생들에게 평가를 50번 받았다',             'RATING_COUNT',       50, NOW(6)),
  ('ACTIVE_30D',      '30일 연속 활동',  '30일 동안 하루도 빠짐없이 세션을 진행했다', 'ACTIVE_STREAK_DAYS', 30, NOW(6)),
  ('FIRST_PAID_ROOM', '유료 방 첫 개설', '처음으로 유료 방을 열었다',                 'PAID_ROOMS',         1,  NOW(6)),
  ('AI_SETS_50',      'AI 세트 50개',    'AI 로 만든 문제 세트가 50개가 됐다',        'AI_QUESTION_SETS',   50, NOW(6));

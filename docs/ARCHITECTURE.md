# 아키텍처

## 모듈 및 패키지
단일 `:app` 모듈이다. `com.routeonomy.app.ui`는 화면/화면 상태, `domain`은 순수 Kotlin 정책과 세션·플래너·퀴즈, `data`는 로컬 JSON 로딩, `pleos`는 공식 SDK 어댑터, `simulation`은 가상 기어·ETA·지역·도착 이벤트를 담당한다. 임시 UI 테마는 중앙 리소스에 둔다.

## 의존성
`ui -> domain`; `data -> domain`; `pleos -> domain`; `simulation -> domain`. 조립 지점인 앱 진입점만 각 구현체를 연결한다. Domain은 Android, UI, JSON 라이브러리, Pleos SDK를 참조하지 않는다. UI는 Pleos SDK를 직접 호출하지 않는다.

## 핵심 계약
Domain 경계의 계약은 기어/ETA/위치/도착 관찰 입력, `SegmentRepository`, `BriefingPlanner`, `SessionController`, `SpeechOutput`, `QuizPolicy`로 작게 유지한다. 실제 구현과 Fake 구현은 같은 입력·출력 의미를 가져야 한다. Pleos SDK에서 확인되지 않은 도착 API는 만들지 않고, 도착은 별도 계약의 Fake 입력으로 둔다.

## 데이터 및 상태 흐름
로컬 JSON -> data 매퍼 -> domain 세그먼트 -> 플래너 -> 세션 재생 큐 -> TTS. Pleos와 Fake 어댑터는 각각 차량 맥락 이벤트를 domain 명령으로 변환한다. 상태는 `gearMode`와 `arrivalStatus`를 독립적으로 관리한다. P 진입은 주차 UI를 열 뿐 도착으로 바꾸지 않는다. `ARRIVED`일 때만 퀴즈를 허용한다.

ETA 변경 시 현재 재생 세그먼트는 완료까지 유지하고, 아직 재생하지 않은 큐만 남은 예산으로 재계획한다. 지역 이벤트는 현재 세그먼트 뒤에 삽입하고 지역 ID별 1회만 처리한다. TTS는 단일 재생 소유자가 순번/세션 ID로 중복 콜백을 무시하고, 일시정지·다음·세션 종료에서 큐와 리소스를 정리한다.

## 오류 처리
SDK 미지원/연결 실패는 상태로 UI에 표시하고 Fake 입력 또는 기본 ETA 15분으로 복구한다. 로컬 JSON 오류는 조용히 무시하지 않고 구체적 오류와 대체 샘플 사용 여부를 로그/디버그 화면에 노출한다. 권한·API 지원 확인은 어댑터 경계에서 수행한다.

# 아키텍처

## 모듈 및 패키지
단일 `:app` 모듈이다. `com.routeonomy.app.ui`는 화면/화면 상태, `domain`은 순수 Kotlin 정책과 세션·플래너·퀴즈, `data`는 로컬 JSON 로딩, `pleos`는 공식 SDK 어댑터, `simulation`은 가상 기어·경로/구간 시간·지역 진입·도착 이벤트를 담당한다. 임시 UI 테마는 중앙 리소스에 둔다.

## 의존성
`ui -> domain`; `data -> domain`; `pleos -> domain`; `simulation -> domain`. 조립 지점인 앱 진입점만 각 구현체를 연결한다. Domain은 Android, UI, JSON 라이브러리, Pleos SDK를 참조하지 않는다. UI는 Pleos SDK를 직접 호출하지 않는다.

## 핵심 계약
Domain 경계의 계약은 기어/경로·구간 시간/지역 진입/도착 관찰 입력, `SegmentRepository`, `BriefingPlanner`, `SessionController`, `SpeechOutput`, `QuizPolicy`로 작게 유지한다. 실제 구현과 Fake 구현은 같은 입력·출력 의미를 가져야 한다. Pleos SDK에서 확인되지 않은 도착 API는 만들지 않고, 도착은 별도 계약의 Fake 입력으로 둔다.

### 더미 경로에서 실제 SDK로의 교체
경로·구간 시간과 지역 진입은 domain의 입력 계약(경로 시나리오 + 현재 구간 남은 시간, 지역 진입 이벤트)으로만 들어온다. MVP는 `simulation`의 더미 구현(로컬 JSON 시나리오, '판교 진입' 버튼)을 쓰고, 이후 `pleos` 어댑터가 같은 계약을 구현한다. 앱 진입점에서 구현체만 바꾸며 domain·ui는 수정하지 않는 것을 목표로 한다. 별도 Gradle 모듈은 만들지 않는다.

교체 후보는 프로젝트가 쓰는 NaviHelper 3.0.5 / navi-data 3.0.4 소스 KDoc에서 확인한 것이며, **AVD·실차 동작은 미검증**이다.
- 구간 시간: `getWaypointInfo` → `onWayPointInfo(List<WaypointInfo>)`의 `remainTime`(KDoc상 초)을 A, `DestinationInfo.remainTime`(KDoc상 초) − A를 B로 쓴다. 지정 지역을 경로에 넣으려면 `addWaypoint`(경로 탐색 권한 필요)가 필요한데, 이는 사용자 내비 경로에 경유지를 추가하는 동작이므로 제품상 허용 여부를 따로 정한다.
- 지역 진입: `onWayPointArrived`, 또는 `onCurrentLocationInfo`의 위·경도를 지역 반경과 비교한다.
- 목적지 도착: `onDestinationArrived`. 퀴즈 허용 조건(`ARRIVED`)의 실제 입력 후보다.
- 경로 위에 지정 지역이 있는지(경로 형상) 판단하는 API는 아직 찾지 못했다.

## 데이터 및 상태 흐름
로컬 JSON(세그먼트·경로 시나리오·지역) -> data 매퍼 -> domain 모델 -> 구간별 플래너(A/B) -> 세션 재생 큐 -> TTS. Pleos와 Fake 어댑터는 각각 차량 맥락 이벤트를 domain 명령으로 변환한다. 상태는 `gearMode`, `arrivalStatus`, 경로 구간 상태(지역 없음 / A: 지역 진입 전 / B: 지역 진입 후)를 서로 독립적으로 관리한다. 구간 상태는 지역 진입 입력으로만 A에서 B로 바뀌며 기어나 도착으로 바뀌지 않는다. P 진입은 주차 UI를 열 뿐 도착으로 바꾸지 않는다. `ARRIVED`일 때만 퀴즈를 허용한다.

구간 시간 변경 시 현재 재생 세그먼트는 완료까지 유지하고, 현재 구간에서 아직 재생하지 않은 큐만 남은 예산으로 재계획한다. 지역 진입 이벤트가 오면 현재 세그먼트를 끝까지 재생한 뒤 남은 A 큐를 버리고 B 구간 계획으로 큐를 교체한다. A 큐가 먼저 비면 진입까지 대기한다. 지역 ID별 진입은 한 주행에 1회만 처리하고 중복 이벤트는 무시한다. TTS는 단일 재생 소유자가 순번/세션 ID로 중복 콜백을 무시하고, 일시정지·다음·세션 종료에서 큐와 리소스를 정리한다.

## 오류 처리
SDK 미지원/연결 실패는 상태로 UI에 표시하고 Fake 입력 또는 기본 ETA 15분으로 복구한다. 로컬 JSON 오류는 조용히 무시하지 않고 구체적 오류와 대체 샘플 사용 여부를 로그/디버그 화면에 노출한다. 권한·API 지원 확인은 어댑터 경계에서 수행한다.

# 아키텍처

## 모듈 및 패키지
단일 `:app` 모듈이다. `com.routeonomy.app.ui`는 화면/화면 상태, `domain`은 순수 Kotlin 정책과 세션·플래너·퀴즈, `data`는 로컬 JSON 로딩, `pleos`는 공식 SDK 어댑터, `simulation`은 가상 기어·경로/구간 시간·지역 진입·도착 이벤트를 담당한다. 임시 UI 테마는 중앙 리소스에 둔다.

## 의존성
`ui -> domain`; `data -> domain`; `pleos -> domain`; `simulation -> domain`. 조립 지점인 앱 진입점만 각 구현체를 연결한다. Domain은 Android, UI, JSON 라이브러리, Pleos SDK를 참조하지 않는다. UI는 Pleos SDK를 직접 호출하지 않는다.

## 핵심 계약
Domain 경계의 계약은 기어/경로·구간 시간/지역 진입/도착 관찰 입력, `SegmentRepository`, `BriefingPlanner`, `SessionController`, `SpeechOutput`, `QuizPolicy`로 작게 유지한다. 실제 구현과 Fake 구현은 같은 입력·출력 의미를 가져야 한다. Pleos SDK에서 확인되지 않은 도착 API는 만들지 않고, 도착은 별도 계약의 Fake 입력으로 둔다.

### 더미 경로에서 실제 SDK로의 교체
경로 판단 입력은 domain의 계약(현재 위치 좌표, 목적지 좌표, 목적지까지 남은 시간, 지역 진입 이벤트)으로만 들어온다. 거리 계산, 경유 예측(우회 비율·후보 제외·선택), 반경 진입 판정, 구간별 계획은 domain의 순수 함수로 구현하고 단위 테스트한다. 즉 판단 로직은 실제 코드이며 더미인 것은 입력뿐이다. MVP는 `simulation`의 더미 구현(로컬 JSON 좌표·남은 시간, 위치 이동 버튼 3개)을 쓰고, 이후 `pleos` 어댑터가 같은 계약을 구현한다. 앱 진입점에서 구현체만 바꾸며 domain·ui는 수정하지 않는 것을 목표로 한다. 별도 Gradle 모듈은 만들지 않는다.

실제 입력 후보는 NaviHelper 3.0.5 / navi-data 3.0.4 소스 KDoc과 공식 문서(document.pleos.ai, 2026-11-11 확인)에서 찾은 것이며, **AVD·실차 동작은 미검증**이다. 지도·경로 API는 Pleos로 한정한다.
- 경로 선형(polyline)이나 구간별 소요 시간을 주는 API는 소스와 문서 어디에도 없다. 그래서 출발 시에는 좌표로 경유를 예측만 하고, A는 조각 단위로 이어 가며, B는 진입 순간의 남은 시간으로 계획한다.
- 목적지 좌표·남은 시간: `getDestinationInfo` → `DestinationInfo(latitude, longitude, remainTime(초), remainDistance(m))`.
- 현재 위치: FusedLocation `registerFusedLocationCallback`(`LocationData(latitude, longitude, …)`) 또는 NaviHelper `onCurrentLocationInfo`.
- 지역 진입: 후보 전체에 FusedLocation `addProximityAlert(ProximityAlertRequest(latitude, longitude, radiusMeters, transitionTypes), ProximityAlertListener)` → `ProximityAlertEvent(alertId, transition)`. 문서 설명은 '지정 영역과 전이에 대한 근접 알림'이다. 권한 `pleos.car.permission.FUSED_LOCATION`.
- 정확도 보강 후보: `requestTrip(TripRequest(지역 좌표…))` → `onTripInfo(TripInfo.eta(초))`. 문서 설명이 'Requests trip.'뿐이라 활성 경로를 바꾸는지 AVD 확인 전에는 쓰지 않는다.
- 쓰지 않는 것: `addWaypoint`. 사용자 내비 경로에 경유지가 실제로 추가되고, 내비 정책은 사용자 동의 없는 자동 경로 전환을 금지한다.
- 목적지 도착: `onDestinationArrived`. 퀴즈 허용 조건(`ARRIVED`)의 실제 입력 후보다.

## 데이터 및 상태 흐름
로컬 JSON(세그먼트·지역 후보·시연 주행) -> data 매퍼 -> domain 모델 -> 경유 예측(표시) -> A 플래너 / 진입 시 B 플래너 -> 세션 재생 큐 -> TTS. Pleos와 Fake 어댑터는 각각 차량 맥락 이벤트를 domain 명령으로 변환한다. 상태는 `gearMode`, `arrivalStatus`, 경로 구간 상태(A: 지역 진입 전 / B: 진입한 지역 ID와 전환 대기 여부)를 서로 독립적으로 관리한다. 구간 상태는 지역 진입 입력으로만 A에서 B로 바뀌며 기어나 도착으로 바뀌지 않는다. P 진입은 주차 UI를 열 뿐 도착으로 바꾸지 않는다. `ARRIVED`일 때만 퀴즈를 허용한다.

남은 시간 변경 시 현재 재생 세그먼트는 완료까지 유지하고, 현재 구간에서 아직 재생하지 않은 큐만 남은 예산으로 재계획한다. 지역 진입 이벤트가 오면 전환 대기로 표시하고, 현재 세그먼트를 끝까지 재생한 뒤 남은 A 큐를 버리고 그 시점의 남은 시간으로 만든 B 계획으로 큐를 교체한다. A 큐가 먼저 비면 진입까지 대기한다. MVP에서 지역 전환은 한 주행에 1회만 처리하고 이후 진입 이벤트는 무시한다. TTS는 단일 재생 소유자가 순번/세션 ID로 중복 콜백을 무시하고, 일시정지·다음·세션 종료에서 큐와 리소스를 정리한다.

## 오류 처리
SDK 미지원/연결 실패는 상태로 UI에 표시하고 Fake 입력 또는 기본 ETA 15분으로 복구한다. 로컬 JSON 오류는 조용히 무시하지 않고 구체적 오류와 대체 샘플 사용 여부를 로그/디버그 화면에 노출한다. 권한·API 지원 확인은 어댑터 경계에서 수행한다.

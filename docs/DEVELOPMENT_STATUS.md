# 개발 현황 및 다음 우선순위

- 기준일: 2026-11-11
- 제출 목표: 2026-11-15 (시연 가능 MVP 2026-11-14)
- 기본 브랜치: `main`

## 현재 진행 상황

| 항목 | 구현 상태 | 검증 상태 | GitHub |
|---|---|---|---|
| F1 기어 모드 | Vehicle SDK 기어 연결과 Fake 기어 입력 | Pleos AVD Virtual Console에서 P/D 전환 반영 확인 | 별도 이슈 없음 |
| F2 ETA | NaviHelper 조회 콜백 연결. 앱은 명시적 테스트 ETA 15분 사용 | `remainTime` 단위 미확인이라 SDK 원시값을 ETA 분으로 쓰지 않음 | 이슈 #1 |
| F3 브리핑 구성 | `segments.json`과 `BriefingPlanner`로 ETA 예산에 맞춰 선택 | 플래너 단위 테스트 통과 | 이슈 #2 흐름에서 사용 |
| F4 TTS 재생 | Pleos TextToSpeech 3.3.1 `ON_DEVICE`, 한국어. 문장 단위 순차 재생, 일시정지·재개·다음 주제, 완주 기록 | `tools/verify.sh` 통과. AVD에서 재생·일시정지·재개·다음 주제·완주 후 자동 진행 확인. 음성 떨림 원인(에뮬레이터 오디오/TTS) 미확인 | 이슈 #2 |
| F5 지역 브리핑 | 플래너에 삽입 함수만 있음. 위치·Fake 이벤트·UI 연결 없음 | 미검증 | 이슈 #3 |
| F6 주행 화면 | 주행 전 대시보드(Figma 기반), 주행 중 큰 ETA·현재 주제·재생 상태만 표시. `distractionOptimized` 선언 | AVD D단 50 km/h에서 화면 유지와 TTS 연속 재생 확인. 주행 중 다음·일시정지 버튼은 현재 숨김(노출 여부 미정). 주행 전 시작 버튼 동작은 미확인 | 이슈 #4 |
| F7 오늘의 발견 | 완주한 항목만 주제·키워드·용어·원문 링크로 표시, P단에서만 표시 | 단위 테스트 4건. AVD에서 완주 표시, 건너뛴 항목 제외, D단 숨김 확인 | 이슈 #5 |
| 도착 후 퀴즈 | 도착(Fake)·P단 조건 판정과 버튼만 있음. 문항·채점 미구현 | 미검증 | 이슈 #6 |
| 제출·시연 | 시연 APK 빌드 가능. 영상·리허설 자료 없음 | 핵심 흐름 전체 리허설 미완료 | 이슈 #7 |

## 다음 작업 순서

1. **도착 후 퀴즈:** 이슈 #6에 따라 로컬 문항·선택·채점·결과를 구현하고 Fake 도착으로 시작 조건을 검증한다. 주차(P)와 도착 상태를 분리한다.
2. **F6 마무리:** 주행 중 다음·일시정지 버튼 노출 여부를 정하고, 주행 전 시작 버튼을 AVD에서 확인한다.
3. **F5 지역 진입:** Fake 판교 진입 이벤트로 현재 항목 뒤 REGION 삽입과 지역별 1회 제한을 연결한다.
4. **F2 ETA:** 단위는 공식 문서나 SDK 근거가 확인될 때만 변환한다. 그 전까지 테스트 ETA 15분을 유지한다.
5. **제출 리허설:** `main` 기준 APK로 주행 전 화면 → 브리핑 → D단 주행 → 주차 후 오늘의 발견/퀴즈 흐름을 AVD에서 리허설하고 영상을 만든다.

## GitHub 상태

- 이전 F4 PR #8, F7 PR #9, 개발 현황 PR #13의 내용은 최신 기준으로 다시 정리해 하나의 통합 PR로 `main`에 반영한다.
- 템플릿 PR #11, #12는 `main`에 병합됐다.
- 기능 이슈 #1~#7은 진행 추적 중이다. 검증이 끝나기 전에 닫지 않는다.
- 제출 시연에는 실제 차량이 없으며 Pleos Connect v2.1.0 AVD를 사용한다.

## 개발 환경 재개

작업 폴더는 `/Users/tokkislove/AndroidStudioProjects/Routeonomy` 하나이며 기본 브랜치는 `main`이다.

노트북을 쉬게 할 때:

1. 열려 있는 파일을 저장하고 Gradle 빌드/동기화가 끝났는지 확인한다.
2. 메모리를 아끼려면 Android Studio를 종료하기 전에 Pleos AVD 창을 닫거나 Device Manager에서 해당 기기의 Stop을 누른다. **Wipe Data, Cold Boot, 삭제는 누르지 않는다.**
3. Android Studio 메뉴에서 **Android Studio > Quit Android Studio**를 누르거나 `⌘Q`를 누른다.

다시 시작할 때:

1. Android Studio에서 **File > Open...**으로 `/Users/tokkislove/AndroidStudioProjects/Routeonomy`를 연다.
2. 프로젝트 동기화가 끝나면 브랜치가 `main` 또는 진행 중인 기능 브랜치인지 확인한다.
3. AVD 확인이 필요한 날에는 **Tools > Device Manager**에서 `Pleos_Connect_v2.1.0` 행의 ▶를 누르고 부팅을 기다린다.
4. 상단 실행 구성에서 `app`, 기기에서 `Pleos Connect v2.1.0`을 선택하고 초록색 ▶ **Run**을 누른다.
5. 주행 확인은 Pleos Virtual Console에서 기어를 D로 바꿔 한다. 앱 안의 시뮬레이션 기어 버튼은 앱 내부 상태만 바꾼다.

새 기능 작업은 `main`에서 기능 이슈에 연결된 브랜치를 만들고 PR로 반영한다.

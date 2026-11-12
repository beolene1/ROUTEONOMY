# Routeonomy

**출퇴근길에 듣는, 남은 시간과 지나가는 지역에 맞춘 경제 브리핑.**

자가용으로 출퇴근하는 직장인을 위한 Pleos Connect 차량용 앱입니다. 목적지까지 남은 시간에 맞춰 산업·경제 이야기를 들려주고, 지나가는 지역에 맞춰 주제를 바꿉니다. 도착해서 주차하면 오늘 들은 이야기를 돌아보고 퀴즈를 풀 수 있습니다.

현대자동차그룹 차량용 앱 개발 공모전 출품작

[시연 순서](docs/DEMO.md) · [시연 경로 GPX](tools/demo-route.gpx) · [검증 현황](docs/DEVELOPMENT_STATUS.md)

## 서울역에서 기흥역까지

| 주행 장면 | 앱에서 볼 수 있는 동작 |
| --- | --- |
| **서울역에서 출발** | 남은 시간에 맞춰 일반 브리핑을 고르고 음성으로 들려줍니다. 경로 그림에는 현재 위치와 판교 구간이 표시됩니다. |
| **판교 진입** | 판교 진입을 감지하면 전환 안내가 나옵니다. 듣던 이야기가 끝나면 판교의 산업·경제 이야기로 넘어갑니다. |
| **기흥역 도착 후 주차** | 목적지 근처에 도착하고 기어를 P로 바꾸면 오늘의 발견과 퀴즈를 열 수 있습니다. 퀴즈는 끝까지 들은 이야기에서 최대 3문항을 냅니다. |

시연은 에뮬레이터에서 GPX를 재생하고 **Pleos FusedLocation**으로 좌표와 근접 알림을 받는 방식입니다. 화면의 남은 시간은 시연 경로의 위치를 바탕으로 앱에서 추정합니다. 원고와 문항은 앱에 포함돼 있어 서버 없이 브리핑을 듣고 퀴즈를 풀 수 있습니다.

## 실행 및 시연

| 항목 | 환경 |
| --- | --- |
| 에뮬레이터 | Pleos Connect v2.1.0, Android 14(API 34), 2560 × 1440 |
| 앱 빌드 | Android Studio, Android SDK 37, Gradle Wrapper |
| 시연 입력 | Extended Controls의 Location, Pleos Virtual Console |

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. Pleos AVD를 Cold Boot로 실행합니다. Extended Controls의 **Location**에서 시작 위치를 서울역으로 설정합니다.
2. Routeonomy를 열고 첫 실행에 표시되는 Pleos 위치 권한을 허용합니다. 출발 화면에서 일반 브리핑을 시작합니다.
3. **Location → Routes**에서 [시연 GPX](tools/demo-route.gpx)를 재생하고, Virtual Console에서 기어를 D로 바꿉니다.
4. 판교에 들어설 때 전환 안내와 다음 브리핑을 확인합니다. 기흥역 근처에 도착한 뒤 기어를 P로 바꾸고 퀴즈를 엽니다.

서울역 좌표, 권장 재생 속도, 위치 수신이 안 될 때의 대체 절차는 [시연 가이드](docs/DEMO.md)에 있습니다.

## Pleos SDK와 검증 범위

| SDK | 맡은 역할 |
| --- | --- |
| Vehicle 2.3.2 | 기어 신호를 받아 주행과 주차 화면을 전환합니다. |
| FusedLocation 1.1.3 | 위치와 근접 알림을 받아 경로 그림을 갱신하고, 판교 진입과 기흥역 근접 도착을 판정합니다. |
| TextToSpeech 3.3.1 | 한국어 브리핑을 문장 단위로 재생합니다. |

Pleos AVD에서 기어 전환, 한국어 TTS, GPX 재생 중 위치 수신과 판교 근접 알림을 확인했습니다. 위치 입력부터 도착 후 퀴즈까지 이어지는 통합 시연은 AVD 리허설 예정입니다.

자동 검증은 단위 테스트, lint, 디버그 APK 빌드를 실행합니다.

```bash
./tools/verify.sh
```

## 개발 문서

- [제품 명세](docs/PRODUCT.md)
- [아키텍처](docs/ARCHITECTURE.md)
- [개발 현황](docs/DEVELOPMENT_STATUS.md)
- [Pleos Connect SDK 공식 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)

팀 [@beolene1](https://github.com/beolene1)

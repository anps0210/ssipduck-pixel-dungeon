# Android 디버그 APK 빌드

## 프로젝트 구조와 공식 빌드 방법

- 루트 `build.gradle`: Android Gradle Plugin 9.2.0, Java 소스 호환성 11,
  compile/target SDK 36, min SDK 21, 게임 버전과 공통 의존성 정의.
- `gradle/wrapper/gradle-wrapper.properties`: Gradle 9.5.0 고정.
- `settings.gradle`: `android`, `core`, `SPD-classes`, `services` 및 desktop/iOS 모듈 등록.
- `android`: APK, Android 실행 진입점, 리소스, 서명 및 네이티브 라이브러리 패키징.
- `core`: 게임 로직과 공용 에셋. `SPD-classes`: 공통 엔진/유틸리티.
- `services`: 뉴스/업데이트 기능. debug 빌드는 기존 debug 서비스 구성을 그대로 사용.

원본의 [Android 공식 빌드 안내](getting-started-android.md)는 Android Studio에서
프로젝트를 열어 실행하거나 서명된 APK/AAB를 만드는 방법을 설명합니다.
[팬게임 변경 권장 사항](recommended-changes.md)은 앱 ID를 Java 패키지 이동 없이
바꿀 수 있으며, 저장 호환성을 위해 버전 코드를 초기화하지 말 것을 설명합니다.
이 프로젝트에서는 Android에만 새 앱 ID/이름을 적용하고 버전 코드는 유지합니다.

CI는 JDK 17에서 저장소에 포함된 Gradle Wrapper로 아래 명령을 실행합니다.
Java 소스 호환성 11과 Gradle 실행용 JDK 17은 서로 다른 설정입니다.

```sh
./gradlew :android:assembleDebug --no-daemon --stacktrace
```

로컬에서는 JDK 17, Android SDK Platform 36 및 Build Tools 36.0.0을 설치하고
`ANDROID_HOME` 또는 무시되는 `local.properties`의 `sdk.dir`로 SDK 위치를 설정하세요.
Windows에서는 `gradlew.bat :android:assembleDebug`를 실행합니다.
출력은 `android/build/outputs/apk/debug/android-debug.apk`입니다.

## 자동 빌드 및 다운로드

`.github/workflows/android-debug.yml`은 다음 경우 실행됩니다.

- `master`에 push될 때(병합 포함).
- `master` 대상으로 PR이 생성/갱신될 때.
- Actions 화면에서 **Run workflow**로 수동 실행할 때.

1. [GitHub Actions](https://github.com/anps0210/ssipduck-pixel-dungeon/actions/workflows/android-debug.yml)에 접속합니다.
2. **Android Debug APK**의 성공한 실행을 선택합니다.
3. 실행 요약 아래 **Artifacts**에서 `ssipduck-debug-실행번호`를 다운로드합니다.
4. ZIP을 풀고 `android-debug.apk`를 Android 기기에 설치합니다.
   필요하면 해당 브라우저/파일 관리자의 '알 수 없는 앱 설치'를 허용합니다.

Artifact 다운로드에는 GitHub 로그인이 필요합니다. 보관 기간은 30일이며,
만료되면 수동 실행으로 다시 빌드할 수 있습니다. 이 APK는 테스트용 디버그 서명이며
Google Play 배포용 릴리스가 아닙니다.

## 앱 식별자와 저장 데이터

- 앱 표시 이름: **Ssipduck Pixel Dungeon**
- Android applicationId: `com.anps0210.ssipduckpixeldungeon`
- 디버그 APK의 최종 ID: `com.anps0210.ssipduckpixeldungeon.indev`
- Java 패키지 및 Android namespace는 원본 그대로 유지합니다.
- 게임 버전 코드 922와 버전 이름 4.0.2 및 기존 debug 접미사를 유지합니다.

원본 녹픽던과 함께 설치할 수 있습니다. 새 ID는 독립된 저장 공간을 사용하므로
원본 저장 데이터를 덮어쓰거나 자동으로 가져오지 않습니다. 게임 저장 형식,
환경 설정 파일명, 백업 처리, 캐릭터/아이템/몬스터 코드는 변경하지 않습니다.
기존 백업 정책도 유지되므로 진행 중인 게임의 자동 백업은 추가되지 않습니다.

CI는 디버그 서명 키를 GitHub Actions 캐시에 재사용하여 같은 서명으로 빌드된
APK를 덮어 설치할 때 팬게임의 데이터를 유지할 수 있게 합니다. 다만 캐시는
영구 저장소가 아니므로 만료/삭제되면 서명 키가 바뀔 수 있고, 로컬에서 만든
APK도 키가 다를 수 있습니다. 서명이 다른 APK는 업데이트 설치할 수 없습니다.
데이터가 중요하다면 앱을 삭제하지 말고, 정식 테스트 배포 전 고정 서명 키를
Secrets로 관리하는 별도 서명 설정을 준비하세요. 앱 삭제 시 로컬 데이터가
사라질 수 있습니다.

## 빌드 실패 확인

Actions 실행의 `build` 작업에서 실패한 단계를 펼칩니다. Gradle은 `--stacktrace`로
오류 원인을 출력합니다. SDK 설치, 의존성 다운로드, Java 컴파일, APK 패키징을
단계별로 확인할 수 있습니다. APK 파일이 없으면 업로드 단계도 실패 처리됩니다.

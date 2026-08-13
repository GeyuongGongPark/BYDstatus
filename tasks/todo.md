# Xcode 프로젝트 생성 및 기본 구조 셋업

## 체크리스트

- [x] 디렉토리 구조 생성 (App, Views, Services, Models)
- [x] Swift 소스 파일 껍데기 생성 (12개 파일)
- [x] project.yml 작성 (xcodegen 설정)
- [x] xcodegen generate 실행
- [x] 빌드 성공 확인

## 결과

BUILD SUCCEEDED — BydStats.xcodeproj 생성 완료

## 참고

- Swift 6 strict concurrency: BGTaskScheduler 등록 클로저에 `@preconcurrency import BackgroundTasks` 적용
- xcodegen이 Info.plist 재생성하므로 권한 키는 직접 파일에 추가해야 함

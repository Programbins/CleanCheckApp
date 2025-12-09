-----

# 🧼 CleanCheck: 온디바이스 AI 기반 손 위생 커버리지 시각화 시스템

## Acknowledgement

> 이 프로젝트는 2025년도 정부(과학기술정보통신부)의 재원으로 정보통신기획평가원의 지원을 받아 수행된 연구임
> (No.RS-2022-00155857, 인공지능융합혁신인재양성(충남대학교)[cite_start]) [cite: 158]
>
> This project was supported by the Institute of Information & Communications Technology Planning & Evaluation (IITP) grant funded by the Korean government (MSIT) (No.RS-2022-00155857, AI Convergence Innovation Talent Training Program at Chungnam National University).

[](https://www.google.com/search?q=%5Bhttps://kotlinlang.org/%5D\(https://kotlinlang.org/\))
[](https://developer.android.com/)
[](https://developer.android.com/jetpack/compose)
[](https://www.tensorflow.org/lite)

> [cite_start]**CleanCheck**는 안드로이드 태블릿 환경에서 동작하는 키오스크형 애플리케이션으로, **YOLOv8**과 **MediaPipe**를 활용한 하이브리드 AI 분석을 통해 손 씻기 과정을 실시간으로 분석하고 세정 커버리지를 시각화합니다[cite: 14, 31].

[cite_start]모든 AI 추론은 **온디바이스(On-device)** 환경에서 수행되어 네트워크 제약 없이 동작하며 개인정보를 안전하게 보호합니다[cite: 19].

-----

## 🌐 CleanCheck

[cleancheck.org](https://cleancheck.org) (Demo)

> [cite_start]**감염 관리의 패러다임을 '사후 분석'에서 '실시간 예방'으로.** [cite: 20]

-----

## 📑 목차

1.  [주요 기능](https://www.google.com/search?q=%23-%EC%A3%BC%EC%9A%94-%EA%B8%B0%EB%8A%A5-key-features)
2.  [시스템 아키텍처](https://www.google.com/search?q=%23-%EC%8B%9C%EC%8A%A4%ED%85%9C-%EC%95%84%ED%82%A4%ED%85%8D%EC%B2%98-system-architecture)
3.  [핵심 기술 스택](https://www.google.com/search?q=%23-%ED%95%B5%EC%8B%AC-%EA%B8%B0%EC%88%A0-%EC%8A%A4%ED%83%9D-tech-stack)
4.  [설치 및 실행](https://www.google.com/search?q=%23-%EC%84%A4%EC%B9%98-%EB%B0%8F-%EC%8B%A4%ED%96%89-installation--usage)
5.  [프로젝트 구조](https://www.google.com/search?q=%23-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8-%EA%B5%AC%EC%A1%B0-project-structure)
6.  [팀 정보](https://www.google.com/search?q=%23-%ED%8C%80-%EC%A0%95%EB%B3%B4-team)

-----

## ✨ 주요 기능 (Key Features)

  - **표준 동작 인식 (Action Recognition)**
    [cite_start]CameraX로 입력된 프레임에서 **YOLOv8n TFLite** 모델을 실행하여 WHO 권장 6단계 손 씻기 동작을 실시간으로 분류하고 절차 준수 여부를 판단합니다[cite: 16, 134].

  - **자율 세정 영역 시각화 (Coverage Visualization)**
    [cite_start]**MediaPipe Hand Landmarker**와 \*\*칼만 필터(Kalman Filter)\*\*를 결합하여 손의 떨림을 보정하고, 기하학적 분석(Convex Hull, Point-in-Polygon)을 통해 세정된 부위를 정밀 판정하여 화면에 오버레이합니다[cite: 17, 138, 143].

  - **사용자 식별 및 데이터 관리 (User Identification)**
    [cite_start]**MobileFaceNets** 기반의 경량화된 얼굴 인식 모델과 **ML Kit**를 사용하여 사용자를 3초 이내에 식별하고 [cite: 62, 65, 82][cite_start], 분석 결과는 **Room Database**에 영구 저장되어 개인별 피드백을 제공합니다[cite: 33, 64].

-----

## 🏗️ 시스템 아키텍처 (System Architecture)

본 시스템은 안드로이드 **MVVM 패턴**을 기반으로 하며, 영상 분석 파이프라인과 UI가 유기적으로 연결되어 있습니다.

*(Note: Replace with actual image from paper Figure 1)*

1.  [cite_start]**Input:** CameraX (Preview + ImageAnalysis) [cite: 43]
2.  **Analysis Engine:**
      * [cite_start]YOLOv8 (Phase/Action Detection) [cite: 46]
      * [cite_start]MediaPipe (Landmark Tracking) [cite: 47]
      * [cite_start]Coverage Engine (Contact Check & Kalman Smoothing) [cite: 51, 54]
3.  [cite_start]**User ID:** ML Kit (Face Detection) + FaceNet TFLite (Embedding) [cite: 57, 58]
4.  [cite_start]**Output:** Jetpack Compose UI + SVG Overlay [cite: 37, 53]

-----

## 🛠️ 핵심 기술 스택 (Tech Stack)

| 구분 | 기술 / 라이브러리 | 상세 설명 |
| :--- | :--- | :--- |
| **Language** | **Kotlin** | 안드로이드 네이티브 앱 개발 언어 |
| **UI Framework** | **Jetpack Compose** | [cite_start]선언형 UI 툴킷을 이용한 반응형 키오스크 인터페이스 구현 [cite: 31, 37] |
| **Camera** | **CameraX** | [cite_start]실시간 프리뷰 및 ImageAnalysis 유즈케이스 활용 [cite: 31, 167] |
| **AI Inference** | **TensorFlow Lite** | [cite_start]YOLOv8 및 MobileFaceNets 모델의 온디바이스 추론 [cite: 46, 58, 134] |
| **Vision** | **MediaPipe Hands** | [cite_start]21개 손 랜드마크 3D 좌표 실시간 추적 [cite: 17, 161] |
| **Database** | **Room** | [cite_start]사용자 정보 및 손 씻기 분석 결과 로컬 저장 [cite: 33, 168] |
| **Face Detection** | **ML Kit** | [cite_start]얼굴 위치 및 각도 감지 (Face Detection API) [cite: 57, 173] |

-----

## 🚀 설치 및 실행 (Installation & Usage)

### 1\. 사전 요구 사항 (Prerequisites)

  - **Android Studio** Koala 이상
  - **Android Device**: Android 10 (API Level 29) 이상 태블릿 권장 (키오스크 모드 최적화)

### 2\. 설치 (Installation)

```bash
# 1) 리포지토리 클론
$ git clone https://github.com/chanwoopark11/CleanCheck.git

# 2) Android Studio에서 프로젝트 열기
# File > Open > 'CleanCheck' 디렉토리 선택

# 3) Gradle Sync 및 빌드
# local.properties 설정 확인 후 Build 진행
```

### 3\. 모델 파일 준비 (Models)

[cite_start]`app/src/main/assets/` 경로에 다음 TFLite 모델 파일들이 위치해야 합니다. [cite: 134, 64]

  - `yolov8n_float16.tflite` (손 씻기 동작 인식)
  - `mobilefacenet.tflite` (사용자 식별)
  - `face_detection_short_range.tflite` (MediaPipe 등 관련 에셋)

-----

## 📂 프로젝트 구조 (Project Structure)

논문의 설계에 따른 안드로이드 프로젝트 구조입니다.

```text
CleanCheck/
├─ app/
│  ├─ src/
│  │  ├─ main/
[cite_start]│  │  │  ├─ assets/          # TFLite 모델 파일 (YOLOv8, MobileFaceNets) [cite: 134]
│  │  │  ├─ java/com/cleancheck/
[cite_start]│  │  │  │  ├─ data/         # Room DB, DAO, DataSources [cite: 33]
│  │  │  │  │  ├─ db/
│  │  │  │  │  └─ repository/
│  │  │  │  ├─ domain/       # 비즈니스 로직 및 유즈케이스
[cite_start]│  │  │  │  ├─ ml/           # AI 분석 엔진 (Hybrid Analysis) [cite: 45]
│  │  │  │  │  ├─ analyzer/  # ImageAnalysis.Analyzer 구현체
│  │  │  │  │  ├─ tflite/    # TFLite Interpreter 래퍼
[cite_start]│  │  │  │  │  ├─ vision/    # MediaPipe, KalmanFilter, ConvexHull [cite: 51, 143]
[cite_start]│  │  │  │  │  └─ face/      # ML Kit & FaceNet 구현 [cite: 48]
[cite_start]│  │  │  │  ├─ ui/           # Jetpack Compose Screens [cite: 37]
│  │  │  │  │  ├─ main/      # 메인 메뉴
│  │  │  │  │  ├─ register/  # 사용자 등록 화면
[cite_start]│  │  │  │  │  ├─ wash/      # 손 씻기 분석 및 오버레이 화면 [cite: 53]
│  │  │  │  │  └─ common/
│  │  │  │  └─ MainActivity.kt
│  │  │  └─ res/
│  └─ build.gradle.kts
└─ README.md
```

-----

## 🧑‍💻 팀 정보 (Team)

| 역할 | 이름 | 연락처 (Email) | 소속 |
| :--- | :--- | :--- | :--- |
| **Project Manager** | **주다빈** | programbins@gmail.com | [cite_start]충남대학교 컴퓨터융합학부 [cite: 3, 4] |
| **AI Modeling** | **최민서** | msc503@naver.com | [cite_start]충남대학교 컴퓨터융합학부 [cite: 3, 4] |
| **Data & QA** | **박찬우** | pcw22600@gmail.com | [cite_start]충남대학교 컴퓨터융합학부 [cite: 3, 4] |
| **Advisor** | **김재정 교수** | jjkim@cnu.ac.kr | [cite_start]충남대학교 / 바이오AI융합연구센터 [cite: 3, 4] |
| **Co-Researcher** | **민영순** | zhzhxh00@naver.com | [cite_start]충남대학교병원 [cite: 3, 4] |

-----

Copyright © 2025 CleanCheck Team. All Rights Reserved.

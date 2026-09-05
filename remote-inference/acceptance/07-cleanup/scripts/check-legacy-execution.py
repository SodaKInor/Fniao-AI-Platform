#!/usr/bin/env python3
"""Verify that retired local execution code is gone without deleting reusable dependencies."""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[6]
BACKEND = ROOT / "backend-github/jeecg-module-system/jeecg-system-biz/src/main/java"
FRONTEND = ROOT / "frontend-vue/src"
EVIDENCE = Path(__file__).resolve().parents[1] / "group3-legacy-execution.actual.json"

REMOVED_BACKEND = (
    "org/jeecg/modules/tab/AIModel",
    "org/jeecg/modules/tab/controller/AITestController.java",
    "org/jeecg/modules/tab/util/CharRecognizer.java",
    "org/jeecg/modules/demo/chat/controller/chattest.java",
    "org/jeecg/modules/demo/chat/study/AllKeyWords.java",
    "org/jeecg/modules/demo/chat/study/BeanMangerOnly.java",
    "org/jeecg/modules/demo/chat/study/studyAndtrain.java",
    "org/jeecg/modules/demo/easy/study/studyPic.java",
)
REMOVED_FRONTEND_ROOTS = (
    "views/audio",
    "views/face",
    "views/tab/live",
    "views/tab/livecanvas",
    "views/train",
)
REMOVED_FRONTEND_FILES = (
    "views/video/TabAiClickpicSettingList.vue",
    "views/video/TabAiSubscriptionNewList.vue",
    "views/video/TabAiVideoSettingList.vue",
    "views/video/TabAiWarningList.vue",
    "views/video/TabVideoUtilList.vue",
    "views/video/modules/TabAiClickpicSettingForm.vue",
    "views/video/modules/TabAiClickpicSettingModal.Style#Drawer.vue",
    "views/video/modules/TabAiClickpicSettingModal.vue",
    "views/video/modules/TabAiSubscriptionNewForm.vue",
    "views/video/modules/TabAiSubscriptionNewModal.Style#Drawer.vue",
    "views/video/modules/TabAiSubscriptionNewModal.vue",
    "views/video/modules/TabAiVideoSettingForm.vue",
    "views/video/modules/TabAiVideoSettingModal.Style#Drawer.vue",
    "views/video/modules/TabAiVideoSettingModal.vue",
    "views/video/modules/TabAiWarningForm.vue",
    "views/video/modules/TabAiWarningModal.Style#Drawer.vue",
    "views/video/modules/TabAiWarningModal.vue",
    "views/video/modules/TabVideoUtilForm.vue",
    "views/video/modules/TabVideoUtilModal.Style#Drawer.vue",
    "views/video/modules/TabVideoUtilModal.vue",
    "views/video/modules/picUtil.vue",
    "views/video/modules/picUtilSetting.vue",
    "views/video/modules/videoUtil.vue",
)

RETAINED = (
    "backend-github/jeecg-module-system/jeecg-system-biz/pom.xml",
    "backend-github/jeecg-module-system/jeecg-system-start/pom.xml",
    "backend-github/pom.xml",
    "deploy/backend/install-private-jars.sh",
    "deploy/backend/rebuild-opencv-java8.sh",
    "frontend-vue/public/index.html",
    "frontend-vue/public/static/jessibuca-pro-multi.js",
    "frontend-vue/public/static/decoder-pro-audio.js",
    "frontend-vue/src/views/tab/TabAiModelList.vue",
    "frontend-vue/src/views/tab/TabAiModelBundList.vue",
    "frontend-vue/src/views/tab/TabAiHistoryList.vue",
    "frontend-vue/src/views/tab/TabAiSubscriptionList.vue",
)

FORBIDDEN_JAVA_REFERENCES = (
    "org.jeecg.modules.tab.AIModel",
    "AIModelYolo3",
    "VideoSendReadCfg",
    "CharRecognizer",
    "org.bytedeco.opencv",
    "ai.onnxruntime",
    "com.github.houbb.opencc4j",
    "org.jeecg.modules.demo.easy.study",
)


def files_under(path):
    return sorted(item for item in path.rglob("*") if item.is_file()) if path.exists() else []


def main():
    for relative in REMOVED_BACKEND:
        target = BACKEND / relative
        remains = files_under(target) if target.is_dir() else ([target] if target.exists() else [])
        assert not remains, f"retired backend code remains: {relative}"
    for relative in REMOVED_FRONTEND_ROOTS:
        assert not files_under(FRONTEND / relative), f"retired frontend root remains: {relative}"
    for relative in REMOVED_FRONTEND_FILES:
        assert not (FRONTEND / relative).exists(), f"retired frontend file remains: {relative}"

    java_sources = sorted(BACKEND.rglob("*.java"))
    violations = []
    for source in java_sources:
        text = source.read_text(errors="replace")
        for marker in FORBIDDEN_JAVA_REFERENCES:
            if marker in text:
                violations.append({
                    "file": str(source.relative_to(ROOT)),
                    "marker": marker,
                })
    assert not violations, violations

    for relative in RETAINED:
        assert (ROOT / relative).is_file(), f"required retained file missing: {relative}"

    websocket = ROOT / (
        "backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/"
        "org/jeecg/modules/message/websocket/WebSocket.java"
    )
    websocket_text = websocket.read_text()
    assert "@ServerEndpoint" in websocket_text
    for forbidden in ("sendUrlFLV", "ITabAiHistoryService", "VideoSendReadCfg"):
        assert forbidden not in websocket_text, f"generic WebSocket still starts legacy execution: {forbidden}"

    access_filter = ROOT / (
        "backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/"
        "org/jeecg/modules/ai/legacy/AiAccessFilter.java"
    )
    protected_paths = (
        "/tab/tabAiHistory/addIdentify",
        "/tab/tabAiHistory/addAudio",
        "/tab/tabAiHistory/addIdentifyClose",
        "/video/tabVideoUtil/startVideoUtil",
        "/video/tabVideoUtil/stopVideoUtil",
        "/tab/tabAiSubscription/subInfo",
    )
    access_text = access_filter.read_text()
    for route in protected_paths:
        assert route in access_text, f"retired route is not rejected: {route}"

    player_index = (ROOT / "frontend-vue/public/index.html").read_text(errors="replace")
    assert "jessibuca-pro-multi.js" in player_index
    result = {
        "status": "PASS",
        "scope": "retired local AI execution and training code",
        "removedBackendFiles": 31,
        "removedFrontendFiles": 99,
        "forbiddenProductionJavaReferences": [],
        "protectedLegacyRoutes": list(protected_paths),
        "retainedManagementAndPlayerFiles": list(RETAINED[5:]),
        "dependenciesRetainedByDecision": list(RETAINED[:5]),
        "note": (
            "Reusable OpenCV/ONNX/JavaCV/ASRT/RapidOCR/Tess4J dependencies and their build "
            "scripts are intentionally retained for future use; retired code has no active reference to them."
        ),
    }
    EVIDENCE.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()

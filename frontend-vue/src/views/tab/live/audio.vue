<template>
  <div id="app">
    <a-layout class="layout">
    <!--  -->
      <a-layout-content >
        <div class="site-layout-content">
          <a-button type="primary" @click="startRecording" :disabled="recording">开始录音</a-button>
          <a-button type="danger" @click="stopRecording" :disabled="!recording">停止录音</a-button>
          <a-divider />
          <div v-if="audioBlob">
            <audio :src="audioUrl" controls style="
    float: left;
    margin: 6px;
    padding: 5px;
    height: 50px;
"></audio>
            <a-button type="primary" @click="uploadAudio" style="height: 50px; margin-left:2%;    float: left;">识别音频</a-button>
               <!--   <a-button type="primary" @click="AiAudio" style="height: 50px; margin-left:2%;    float: left;">识别音频</a-button><br/><br/> -->
            <textarea v-model="aitext"  placeholder="识别内容输出" style="width: 100%;height:500px;"></textarea>
          </div>
          <a-message />
        </div>
      </a-layout-content>
      <a-layout-footer style="text-align: center">
     
      </a-layout-footer>
    </a-layout>
  </div>
</template>

<script>
import axios from 'axios';
import { message } from 'ant-design-vue';
  import {
    httpAction,
    getAction,
    uploadAction 
  } from '@/api/manage'
export default {
  data() {
    return {
      mediaRecorder: null,
      audioBlob: null,
      audioUrl: null,
      recording: false,
      aitext:""
    };
  },
  methods: {
    startRecording() {
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        message.error('浏览器不支持音频录制功能');
        return;
      }

      navigator.mediaDevices.getUserMedia({ audio: true })
        .then(stream => {
          this.mediaRecorder = new MediaRecorder(stream);
          this.mediaRecorder.ondataavailable = this.handleDataAvailable;
          this.mediaRecorder.start();
          this.recording = true;
          message.info('录音开始');
        })
        .catch(error => {
          console.error('无法访问麦克风', error);
          message.error('无法访问麦克风或用户拒绝权限');
        });
    },
    stopRecording() {
      if (this.mediaRecorder) {
        this.mediaRecorder.stop();
        this.recording = false;
        message.info('录音停止');
      }
    },
    handleDataAvailable(event) {
      this.audioBlob = new Blob([event.data], { type: 'audio/wav' });
      this.audioUrl = URL.createObjectURL(this.audioBlob);
    },
    uploadAudio() {
      message.warning('旧音频执行入口已停用');
    },
    AiAudio() {
      message.warning('旧音频执行入口已停用');
    }
  }
};
</script>

<style>
#app {
  min-height: 100vh;
}

.site-layout-content {
  background: #fff;
  padding: 24px;
  margin: 0;
}
</style>

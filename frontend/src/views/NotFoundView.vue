<template>
  <div class="notfound-page">
    <div class="wrapper">
      <div class="four-oh-four">404</div>

      <div class="characters">
        <div class="character char-orange">
          <div class="eyes dizzy">
            <div class="eyeball"><div class="pupil"></div></div>
            <div class="eyeball"><div class="pupil"></div></div>
          </div>
          <div class="mouth"></div>
        </div>

        <div class="character char-purple">
          <div class="eyes dizzy">
            <div class="eyeball"><div class="pupil"></div></div>
            <div class="eyeball"><div class="pupil"></div></div>
          </div>
          <div class="mouth"></div>
        </div>

        <div class="character char-black">
          <div class="eyes dizzy">
            <div class="eyeball"><div class="pupil"></div></div>
            <div class="eyeball"><div class="pupil"></div></div>
          </div>
          <div class="mouth"></div>
        </div>

        <div class="character char-yellow">
          <div class="eyes dizzy">
            <div class="eyeball"><div class="pupil"></div></div>
            <div class="eyeball"><div class="pupil"></div></div>
          </div>
          <div class="mouth"></div>
        </div>
      </div>

      <div class="message">
        <h1>页面未找到</h1>
        <p>您访问的页面不存在。</p>
        <div class="btn-group">
          <button class="btn btn-primary" type="button" @click="router.push('/login')">去登录</button>
          <button class="btn btn-secondary" type="button" @click="goBack">返回上一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/dashboard')
}
</script>

<style scoped>
.notfound-page {
  min-height: 100vh;
  background: #181821;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

.wrapper {
  width: 100%;
  max-width: 650px;
  padding: 80px 50px;
  text-align: center;
}

.four-oh-four {
  font-size: 160px;
  font-weight: 800;
  line-height: 1;
  color: #353549;
  margin-bottom: 40px;
}

.characters {
  display: flex;
  justify-content: center;
  align-items: flex-end;
  gap: 24px;
  margin-bottom: 50px;
  height: 150px;
}

.character {
  position: relative;
  border-radius: 8px 8px 0 0;
}

.char-purple {
  width: 65px;
  height: 130px;
  background: #6c3ff5;
}

.char-black {
  width: 48px;
  height: 100px;
  background: #2d2d2d;
}

.char-orange {
  width: 85px;
  height: 75px;
  background: #ff9b6b;
  border-radius: 43px 43px 0 0;
}

.char-yellow {
  width: 55px;
  height: 85px;
  background: #e8d754;
  border-radius: 28px 28px 0 0;
}

.eyes {
  position: absolute;
  display: flex;
  gap: 8px;
  left: 50%;
  transform: translateX(-50%);
  top: 18px;
}

.char-purple .eyes {
  top: 25px;
}

.eyeball {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  position: relative;
}

.pupil {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #1a1a2e;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.dizzy .eyeball::after {
  content: 'x';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-family: monospace;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1;
}

.dizzy .pupil {
  display: none;
}

.mouth {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2d2d2d;
  top: 36px;
}

.char-purple .mouth {
  top: 50px;
}

.char-black .mouth,
.char-yellow .mouth {
  width: 5px;
  height: 5px;
}

.message h1 {
  margin: 0 0 10px;
  font-size: 28px;
  font-weight: 600;
}

.message p {
  margin: 0 0 28px;
  color: #888;
  font-size: 15px;
}

.btn-group {
  display: flex;
  justify-content: center;
  gap: 14px;
}

.btn {
  border: 0;
  border-radius: 6px;
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 500;
  transition: opacity 0.2s;
  cursor: pointer;
}

.btn-primary {
  color: #fff;
  background: #6c3ff5;
}

.btn-secondary {
  color: #aaa;
  background: #333;
}

.btn:hover {
  opacity: 0.82;
}

@media (max-width: 480px) {
  .wrapper {
    padding: 40px 18px;
  }

  .four-oh-four {
    font-size: 100px;
  }

  .characters {
    gap: 10px;
  }

  .char-purple {
    width: 45px;
    height: 80px;
  }

  .char-black {
    width: 32px;
    height: 64px;
  }

  .char-orange {
    width: 56px;
    height: 48px;
    border-radius: 28px 28px 0 0;
  }

  .char-yellow {
    width: 36px;
    height: 56px;
    border-radius: 18px 18px 0 0;
  }

  .btn-group {
    flex-direction: column;
  }
}
</style>

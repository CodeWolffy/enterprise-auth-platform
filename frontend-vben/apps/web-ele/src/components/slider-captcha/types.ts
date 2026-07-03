/** 滑块验证码相关类型（对齐后端 /api/auth/captcha 契约） */

/**
 * 滑块验证码交互状态：
 * - ready     等待用户拖动
 * - verifying 轨迹提交后端校验中
 * - success   校验通过（短暂展示后关闭）
 * - error     校验失败（短暂展示后重置）
 */
export type CaptchaStatus = 'error' | 'ready' | 'success' | 'verifying';

export interface CaptchaTrackPoint {
  x: number;
  y: number;
  t: number;
  type: string;
}

export interface CaptchaTrackPayload {
  bgImageWidth: number;
  bgImageHeight: number;
  templateImageWidth: number;
  templateImageHeight: number;
  startTime: number;
  stopTime: number;
  left: number;
  top: number;
  trackList: CaptchaTrackPoint[];
}

/** 后端 GET /api/auth/captcha 返回值 */
export interface CaptchaResponse {
  captchaId: string;
  backgroundImage: string;
  sliderImage: string;
  backgroundImageWidth: number;
  backgroundImageHeight: number;
  sliderImageWidth: number;
  sliderImageHeight: number;
}

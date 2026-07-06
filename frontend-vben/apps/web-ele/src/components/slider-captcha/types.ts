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
  /** 'MOVE' 滑动轨迹点 / 'CLICK' 文字点选点 */
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

/** 验证码类型：滑块拼图 / 文字点选（对齐后端 tianai CaptchaTypeConstant） */
export type CaptchaType = 'SLIDER' | 'WORD_IMAGE_CLICK';

/** 后端 GET /api/auth/captcha 返回值 */
export interface CaptchaResponse {
  captchaId: string;
  /** 背景图：滑块为拼图底图；点选为绘有待点选文字的底图 */
  backgroundImage: string;
  /** 模板图：滑块为拼图块；点选为提示图(按序展示待点文字) */
  sliderImage: string;
  backgroundImageWidth: number;
  backgroundImageHeight: number;
  sliderImageWidth: number;
  sliderImageHeight: number;
  /** 验证码类型，缺省按 SLIDER 处理 */
  type?: CaptchaType;
}

export default {
  extends: ['@vben/stylelint-config'],
  overrides: [
    {
      files: [
        'apps/web-ele/src/components/slider-captcha/index.vue',
        'apps/web-ele/src/views/auth/*.vue',
        'apps/web-ele/src/views/_core/authentication/*.vue',
      ],
      rules: {
        // css-tree 3.1.0 对 cursor 语法引用解析失败，本文件内关闭该校验
        'declaration-property-value-no-unknown': null,
      },
    },
  ],
  root: true,
};

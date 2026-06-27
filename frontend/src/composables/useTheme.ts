import { computed, ref } from 'vue'

const STORAGE_DARK_KEY = 'eap_theme_dark'
const STORAGE_BRAND_KEY = 'eap_brand_color'

const currentBrandColor = ref<string>('')
const isDark = ref<boolean>(false)

function applyDarkMode(dark: boolean) {
  const html = document.documentElement
  if (dark) {
    html.classList.add('dark')
  } else {
    html.classList.remove('dark')
  }
}

export function useTheme() {
  function setBrandColor(color: string | null) {
    if (!color) {
      document.documentElement.style.removeProperty('--el-color-primary')
      document.documentElement.style.removeProperty('--primary')
      currentBrandColor.value = ''
      localStorage.removeItem(STORAGE_BRAND_KEY)
      return
    }

    document.documentElement.style.setProperty('--el-color-primary', color)
    document.documentElement.style.setProperty('--primary', color)
    currentBrandColor.value = color
    localStorage.setItem(STORAGE_BRAND_KEY, color)
  }

  function setDarkMode(dark: boolean) {
    isDark.value = dark
    applyDarkMode(dark)
    localStorage.setItem(STORAGE_DARK_KEY, dark ? '1' : '0')
  }

  function toggleDark() {
    setDarkMode(!isDark.value)
  }

  function initTheme() {
    const savedColor = localStorage.getItem(STORAGE_BRAND_KEY)
    if (savedColor) {
      setBrandColor(savedColor)
    }

    const savedDark = localStorage.getItem(STORAGE_DARK_KEY)
    if (savedDark !== null) {
      isDark.value = savedDark === '1'
    } else {
      isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    applyDarkMode(isDark.value)
  }

  return {
    currentBrandColor: computed(() => currentBrandColor.value),
    isDark: computed(() => isDark.value),
    initTheme,
    setBrandColor,
    setDarkMode,
    toggleDark,
  }
}

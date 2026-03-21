import { ref } from 'vue'

const currentBrandColor = ref<string>('')

export function useTheme() {
  function setBrandColor(color: string | null) {
    if (!color) {
      document.documentElement.style.removeProperty('--el-color-primary')
      currentBrandColor.value = ''
      localStorage.removeItem('eap_brand_color')
      return
    }

    document.documentElement.style.setProperty('--el-color-primary', color)
    currentBrandColor.value = color
    localStorage.setItem('eap_brand_color', color)
  }

  function initTheme() {
    const savedColor = localStorage.getItem('eap_brand_color')
    if (savedColor) {
      setBrandColor(savedColor)
    }
  }

  return {
    currentBrandColor,
    setBrandColor,
    initTheme,
  }
}

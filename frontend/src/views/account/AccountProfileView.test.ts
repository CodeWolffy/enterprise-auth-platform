import { describe, expect, it, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import AccountProfileView from './AccountProfileView.vue'

const fetchAccountProfile = vi.fn()
const querySessions = vi.fn()
const changeAccountPassword = vi.fn()

vi.mock('@/api/modules', async () => {
  const actual = await vi.importActual<typeof import('@/api/modules')>('@/api/modules')
  return {
    ...actual,
    fetchAccountProfile,
    querySessions,
    changeAccountPassword,
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn(() => ({
    passwordChangeRequired: true,
    passwordChangeReason: null,
    tenantId: 'tenant-a',
    snapshot: {
      userId: 1,
      username: 'tester',
      tenantId: 'tenant-a',
      operatorTenantId: 'tenant-a',
      superAdmin: false,
      roles: ['USER'],
      grants: [],
      dataScopeType: 'ALL',
      customDeptIds: [],
      menus: [],
    },
    clearPasswordChangeRequirement: vi.fn(),
    bootstrapSnapshot: vi.fn(),
  })),
}))

vi.mock('vue-router', () => ({
  useRouter: vi.fn(() => ({
    replace: vi.fn(),
    push: vi.fn(),
  })),
}))

describe('AccountProfileView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchAccountProfile.mockReset()
    querySessions.mockReset()
    changeAccountPassword.mockReset()
  })

  it('强制改密态下不初始化个人资料和在线会话请求', async () => {
    shallowMount(AccountProfileView, {
      global: {
        stubs: {
          'el-table': true,
          'el-table-column': true,
          'el-drawer': true,
          'el-dialog': true,
        },
      },
    })
    await nextTick()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(fetchAccountProfile).not.toHaveBeenCalled()
    expect(querySessions).not.toHaveBeenCalled()
  })
})

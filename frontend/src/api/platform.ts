/** @deprecated 按域导入 API 模块以获得更清晰的依赖关系，例如从 @/api/modules/user 导入 queryUsers。仍支持直接从 @/api/platform 导入全部函数。 */

export * from './modules/user'
export * from './modules/role'
export * from './modules/dept'
export * from './modules/tenant'
export * from './modules/resource'
export * from './modules/audit'
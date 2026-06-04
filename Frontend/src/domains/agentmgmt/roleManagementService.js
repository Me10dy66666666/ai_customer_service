import http from '@/core/axios'

export function listRoles() {
  return http.get('/api/admin/roles')
}

export function createRole(data) {
  return http.post('/api/admin/roles', data)
}

export function updateRole(id, data) {
  return http.put(`/api/admin/roles/${id}`, data)
}

export function deleteRole(id) {
  return http.delete(`/api/admin/roles/${id}`)
}

export function getRolePermissions(roleId) {
  return http.get(`/api/admin/roles/${roleId}/permissions`)
}

export function setRolePermissions(roleId, permissionIds) {
  return http.put(`/api/admin/roles/${roleId}/permissions`, { permissionIds })
}

export function listAllPermissions() {
  return http.get('/api/admin/permissions')
}

export function assignUserRoles(userId, roleIds) {
  return http.put(`/api/admin/users/${userId}/roles`, { roleIds })
}

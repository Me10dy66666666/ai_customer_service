export const ROLE_OPTIONS = [
  { value: 'AGENT', label: '客服' },
  { value: 'KB_ADMIN', label: '知识库管理员' }
]

export const ROLE_LABEL_MAP = {
  'ADMIN': '超级管理员',
  'KB_ADMIN': '知识库管理员',
  'AGENT': '客服',
  'USER': '注册用户',
  'VIP': 'VIP会员'
}

export const ROLE_PERMISSION_MAP = {
  'AGENT': [],
  'KB_ADMIN': [
    'knowledge:upload',
    'knowledge:review',
    'knowledge:read',
    'knowledge:delete'
  ]
}

export const ALL_PERMISSIONS = [
  { code: 'knowledge:upload', name: '知识库文档上传', resource: 'knowledge' },
  { code: 'knowledge:review', name: '知识库文档审核', resource: 'knowledge' },
  { code: 'knowledge:read', name: '知识库文档查阅', resource: 'knowledge' },
  { code: 'knowledge:delete', name: '知识库文档删除', resource: 'knowledge' },
  { code: 'user:create', name: '创建用户', resource: 'user' },
  { code: 'user:update', name: '编辑用户', resource: 'user' },
  { code: 'user:delete', name: '删除用户', resource: 'user' },
  { code: 'role:manage', name: '角色权限管理', resource: 'role' },
  { code: 'order:read', name: '查看订单', resource: 'order' },
  { code: 'work_order:manage', name: '工单管理', resource: 'work_order' }
]

export const ROLE_STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: '1', label: '启用' },
  { value: '0', label: '禁用' }
]

export const ROLE_FILTER_OPTIONS = [
  { value: '', label: '全部角色' },
  { value: 'AGENT', label: '客服' },
  { value: 'KB_ADMIN', label: '知识库管理员' },
  { value: 'ADMIN', label: '超级管理员' }
]

export const CATEGORY_EN_TO_CN = {
  'product': '产品手册',
  'policy': '售后政策',
  'guide': '操作指南',
  'faq': 'FAQ',
  'other': '其他',
}

export function toDisplayCategory(cat) {
  if (!cat) return ''
  return CATEGORY_EN_TO_CN[cat] || cat
}
